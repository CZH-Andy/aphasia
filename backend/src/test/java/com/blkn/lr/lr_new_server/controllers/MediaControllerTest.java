package com.blkn.lr.lr_new_server.controllers;

import com.blkn.lr.lr_new_server.exception.ForbiddenException;
import com.blkn.lr.lr_new_server.exception.GlobalExceptionHandler;
import com.blkn.lr.lr_new_server.services.MediaServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MediaControllerTest {
    private MockMvc mvc;
    private MediaServices mediaServices;

    @BeforeEach
    void setUp() {
        mediaServices = mock(MediaServices.class);
        mvc = MockMvcBuilders.standaloneSetup(new MediaController(mediaServices))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldStreamSignedImageWithPrivateCacheHeaders() throws Exception {
        when(mediaServices.openSigned("images", "doctor-1", "a.png", 123L, "sig"))
                .thenReturn(new MediaServices.MediaDownload(
                        new ByteArrayResource(new byte[]{1, 2, 3}),
                        MediaType.IMAGE_PNG,
                        60));

        mvc.perform(get("/images/doctor-1/a.png")
                        .param("expires", "123")
                        .param("signature", "sig"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[]{1, 2, 3}))
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", "max-age=60, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void shouldSupportAudioRangeRequests() throws Exception {
        when(mediaServices.openSigned("audio", "doctor-1", "a.mp3", 123L, "sig"))
                .thenReturn(new MediaServices.MediaDownload(
                        new ByteArrayResource(new byte[]{1, 2, 3, 4}),
                        MediaType.valueOf("audio/mpeg"),
                        60));

        mvc.perform(get("/audio/doctor-1/a.mp3")
                        .param("expires", "123")
                        .param("signature", "sig")
                        .header("Range", "bytes=1-2"))
                .andExpect(status().isPartialContent())
                .andExpect(content().bytes(new byte[]{2, 3}))
                .andExpect(header().string("Content-Range", "bytes 1-2/4"));
    }

    @Test
    void shouldRejectUnsignedMediaRequest() throws Exception {
        mvc.perform(get("/audio/doctor-1/a.mp3"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("媒体链接缺少签名"));
    }

    @Test
    void shouldReturnForbiddenForInvalidSignature() throws Exception {
        when(mediaServices.openSigned("audio", "doctor-1", "a.mp3", 123L, "bad"))
                .thenThrow(new ForbiddenException("媒体链接无效或已过期"));

        mvc.perform(get("/audio/doctor-1/a.mp3")
                        .param("expires", "123")
                        .param("signature", "bad"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("媒体链接无效或已过期"));
    }
}
