package com.blkn.lr.lr_new_server.controllers;

import com.blkn.lr.lr_new_server.dto.common.UserDto;
import com.blkn.lr.lr_new_server.dto.request.LoginRequest;
import com.blkn.lr.lr_new_server.exception.GlobalExceptionHandler;
import com.blkn.lr.lr_new_server.services.AccountServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AccountController 路由测试。
 * <p>login: 密码只从 JSON body 读取；Token 使用独立端点和兼容入口。
 * register: 关心 @Valid body 透传到 Service。
 */
class AccountControllerTest {

    private MockMvc mvc;
    private AccountServices service;

    @BeforeEach
    void setUp() {
        service = mock(AccountServices.class);
        AccountController controller = new AccountController(service);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void loginShouldReadJsonBodyAndDisableCaching() throws Exception {
        when(service.loginWithPassword(any())).thenReturn(new UserDto());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identity\":\"alice\",\"password\":\"pwd\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"));

        ArgumentCaptor<LoginRequest> captor = ArgumentCaptor.forClass(LoginRequest.class);
        verify(service).loginWithPassword(captor.capture());
        LoginRequest request = captor.getValue();
        assertEquals("alice", request.getIdentity());
        assertEquals("pwd", request.getPassword());
    }

    @Test
    void loginShouldRejectInvalidPayloadBeforeCallingService() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identity\":\"alice\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void tokenEndpointShouldForwardTokenAndDisableCaching() throws Exception {
        when(service.loginWithToken("tok")).thenReturn(new UserDto());

        mvc.perform(post("/api/auth/token").header("Token", "tok"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));

        verify(service).loginWithToken("tok");
    }

    @Test
    void legacyAuthShouldOnlyAcceptTokenAndAdvertiseReplacement() throws Exception {
        when(service.loginWithToken("tok")).thenReturn(new UserDto());

        mvc.perform(post("/api/auth")
                        .header("Token", "tok")
                        .header("identity", "ignored")
                        .header("password", "ignored"))
                .andExpect(status().isOk())
                .andExpect(header().string("Deprecation", "@1784217600"))
                .andExpect(header().string("Link", "</api/auth/token>; rel=\"successor-version\""));

        verify(service).loginWithToken("tok");
    }

    @Test
    void legacyAuthShouldRejectPasswordHeaders() throws Exception {
        mvc.perform(post("/api/auth")
                        .header("identity", "alice")
                        .header("password", "pwd"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));

        verifyNoInteractions(service);
    }

    @Test
    void registerShouldForwardBodyToService() throws Exception {
        when(service.register(any())).thenReturn(new UserDto());

        mvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identity\":\"bob\",\"password\":\"pwd\",\"role\":1}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));

        verify(service).register(any());
    }

    @Test
    void registerShouldRejectDoctorRole() throws Exception {
        mvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identity\":\"bob\",\"password\":\"pwd\",\"role\":2}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
