package com.blkn.lr.lr_new_server.controllers;

import com.blkn.lr.lr_new_server.dto.models.media.MediaFileDto;
import com.blkn.lr.lr_new_server.exception.GlobalExceptionHandler;
import com.blkn.lr.lr_new_server.exception.FileTypeException;
import com.blkn.lr.lr_new_server.services.MediaServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FileController 测试。
 *
 * <p>四个 endpoint：
 * <ul>
 *   <li>POST /api/image：调 {@code uploadFile} 分支 image → {@code fileDao.createImageFile}</li>
 *   <li>POST /api/audio：调 {@code uploadFile} 分支 audio → {@code fileDao.createAudioFile}</li>
 *   <li>GET /api/images：列出当前 uid 的图片 URL + name</li>
 *   <li>GET /api/audios：列出当前 uid 的音频 URL + name</li>
 * </ul>
 *
 * <p>控制器只负责路由到 {@link MediaServices}；文件头校验、落盘和签名 URL
 * 生成均由服务层负责。
 */
class FileControllerTest {

    private MockMvc mvc;
    private MediaServices mediaServices;

    private static final String UID = "user-7";

    @BeforeEach
    void setUp() {
        mediaServices = mock(MediaServices.class);

        FileController controller = new FileController(mediaServices);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ============================================================
    // POST /api/image —— image/* content-type 走 createImageFile 分支
    // ============================================================

    @Test
    void uploadImagesShouldRouteImageContentTypeToCreateImageFile() throws Exception {
        when(mediaServices.saveImage(any(), org.mockito.ArgumentMatchers.eq(UID)))
                .thenReturn(new MediaFileDto(
                        "abc.png",
                        "http://localhost:8080/images/" + UID + "/abc.png?expires=123&signature=sig"));

        MockMultipartFile mf = new MockMultipartFile("file", "abc.png", "image/png", new byte[]{1, 2, 3});
        mvc.perform(multipart("/api/image").file(mf).requestAttr("uid", UID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("abc.png"))
                .andExpect(jsonPath("$.url").value(
                        "http://localhost:8080/images/" + UID + "/abc.png?expires=123&signature=sig"));
        verify(mediaServices).saveImage(any(), org.mockito.ArgumentMatchers.eq(UID));
    }

    @Test
    void uploadImagesShouldReturn400WhenFileDaoRejectsContent() throws Exception {
        MockMultipartFile mf = new MockMultipartFile("file", "x.txt", "text/plain", new byte[]{1, 2});
        when(mediaServices.saveImage(any(), org.mockito.ArgumentMatchers.eq(UID)))
                .thenThrow(new FileTypeException("文件内容不是受支持的图片"));

        mvc.perform(multipart("/api/image").file(mf).requestAttr("uid", UID))
                .andExpect(status().isBadRequest());
    }

    // ============================================================
    // POST /api/audio —— audio/* content-type 走 createAudioFile 分支
    // ============================================================

    @Test
    void uploadAudioShouldRouteAudioContentTypeToCreateAudioFile() throws Exception {
        when(mediaServices.saveAudio(any(), org.mockito.ArgumentMatchers.eq(UID)))
                .thenReturn(new MediaFileDto(
                        "xyz.wav",
                        "http://localhost:8080/audio/" + UID + "/xyz.wav?expires=123&signature=sig"));

        MockMultipartFile mf = new MockMultipartFile("file", "xyz.wav", "audio/wav", new byte[]{5, 6});
        mvc.perform(multipart("/api/audio").file(mf).requestAttr("uid", UID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("xyz.wav"))
                .andExpect(jsonPath("$.url").value(
                        "http://localhost:8080/audio/" + UID + "/xyz.wav?expires=123&signature=sig"));
        verify(mediaServices).saveAudio(any(), org.mockito.ArgumentMatchers.eq(UID));
    }

    @Test
    void uploadImagesShouldNeverRouteToAudioStorage() throws Exception {
        when(mediaServices.saveImage(any(), org.mockito.ArgumentMatchers.eq(UID)))
                .thenThrow(new FileTypeException("文件内容不是受支持的图片"));

        MockMultipartFile mf = new MockMultipartFile("file", "audio-via-image.wav", "audio/wav", new byte[]{1});
        mvc.perform(multipart("/api/image").file(mf).requestAttr("uid", UID))
                .andExpect(status().isBadRequest());

        verify(mediaServices, never()).saveAudio(any(), org.mockito.ArgumentMatchers.eq(UID));
    }

    // ============================================================
    // GET /api/images / /api/audios —— list 模式
    // ============================================================

    @Test
    void getAllImageInfoShouldSplitPathAndPrependUrlPrefix() throws Exception {
        when(mediaServices.listImages(UID))
                .thenReturn(List.of(
                        new MediaFileDto("a.png", "http://localhost:8080/images/" + UID + "/a.png?expires=123&signature=a"),
                        new MediaFileDto("b.jpg", "http://localhost:8080/images/" + UID + "/b.jpg?expires=123&signature=b")));

        mvc.perform(get("/api/images").requestAttr("uid", UID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("a.png"))
                .andExpect(jsonPath("$[0].url").value(
                        "http://localhost:8080/images/" + UID + "/a.png?expires=123&signature=a"))
                .andExpect(jsonPath("$[1].name").value("b.jpg"))
                .andExpect(jsonPath("$[1].url").value(
                        "http://localhost:8080/images/" + UID + "/b.jpg?expires=123&signature=b"));
    }

    @Test
    void getAllImageInfoShouldReturnEmptyArrayWhenNoImages() throws Exception {
        when(mediaServices.listImages(UID)).thenReturn(List.of());

        mvc.perform(get("/api/images").requestAttr("uid", UID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getAllAudioInfoShouldSplitPathAndPrependUrlPrefix() throws Exception {
        when(mediaServices.listAudios(UID))
                .thenReturn(List.of(new MediaFileDto(
                        "c.wav",
                        "http://localhost:8080/audio/" + UID + "/c.wav?expires=123&signature=c")));

        mvc.perform(get("/api/audios").requestAttr("uid", UID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("c.wav"))
                .andExpect(jsonPath("$[0].url").value(
                        "http://localhost:8080/audio/" + UID + "/c.wav?expires=123&signature=c"));
    }

    @Test
    void getAllAudioInfoShouldReturnEmptyArrayWhenNoAudios() throws Exception {
        when(mediaServices.listAudios(UID)).thenReturn(List.of());

        mvc.perform(get("/api/audios").requestAttr("uid", UID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
