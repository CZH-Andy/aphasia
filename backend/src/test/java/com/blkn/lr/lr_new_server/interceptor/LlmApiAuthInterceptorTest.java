package com.blkn.lr.lr_new_server.interceptor;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.blkn.lr.lr_new_server.controllers.LLMController;
import com.blkn.lr.lr_new_server.services.LLMService;
import com.blkn.lr.lr_new_server.util.TokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LlmApiAuthInterceptorTest {

    private MockMvc mockMvc;
    private LLMService llmService;

    @BeforeEach
    void setUp() {
        llmService = Mockito.mock(LLMService.class);
        LLMController controller = new LLMController(llmService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new TokenInterceptor(TokenUtil.forSecret("test-secret")))
                .build();
    }

    @Test
    void shouldReturn401WhenNoTokenForDiagnose2() throws Exception {
        mockMvc.perform(post("/api/diagnose2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversation\":\"测试会话\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer realm=\"aphasia-api\""));

        verifyNoInteractions(llmService);
    }

    @Test
    void shouldReturn401WhenInvalidBearerTokenForDiagnose2() throws Exception {
        mockMvc.perform(post("/api/diagnose2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversation\":\"测试会话\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer realm=\"aphasia-api\", error=\"invalid_token\""));

        verifyNoInteractions(llmService);
    }

    @Test
    void shouldReturn400WhenAuthorizationHeaderIsMalformed() throws Exception {
        mockMvc.perform(post("/api/diagnose2")
                        .header(HttpHeaders.AUTHORIZATION, "Basic credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversation\":\"测试会话\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer realm=\"aphasia-api\", error=\"invalid_request\""));

        verifyNoInteractions(llmService);
    }

    @Test
    void shouldReturn401WhenSignedTokenMissesRequiredClaims() throws Exception {
        String tokenWithoutRole = JWT.create()
                .withIssuer("aphasia")
                .withClaim("uid", "doctor-uid")
                .sign(Algorithm.HMAC256("test-secret"));

        mockMvc.perform(post("/api/diagnose2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenWithoutRole)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversation\":\"测试会话\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer realm=\"aphasia-api\", error=\"invalid_token\""));

        verifyNoInteractions(llmService);
    }
}
