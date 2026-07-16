package com.blkn.lr.lr_new_server.controllers;

import com.blkn.lr.lr_new_server.config.StaticResourcesConfig;
import com.blkn.lr.lr_new_server.exception.ForbiddenException;
import com.blkn.lr.lr_new_server.services.MediaServices;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * 不在 /api 下：浏览器图片控件和原生音频播放器无法统一附带 Token。
 * 访问权限由 URL 中的短期 HMAC 签名控制。
 */
@RestController
@RequiredArgsConstructor
public class MediaController {
    private final MediaServices mediaServices;

    @GetMapping("/images/{uid}/{fileName:.+}")
    ResponseEntity<org.springframework.core.io.Resource> image(
            @PathVariable String uid,
            @PathVariable String fileName,
            @RequestParam(required = false) Long expires,
            @RequestParam(required = false) String signature) {
        return download(StaticResourcesConfig.IMAGE_DIR, uid, fileName, expires, signature);
    }

    @GetMapping("/audio/{uid}/{fileName:.+}")
    ResponseEntity<org.springframework.core.io.Resource> audio(
            @PathVariable String uid,
            @PathVariable String fileName,
            @RequestParam(required = false) Long expires,
            @RequestParam(required = false) String signature) {
        return download(StaticResourcesConfig.AUDIO_DIR, uid, fileName, expires, signature);
    }

    private ResponseEntity<org.springframework.core.io.Resource> download(
            String mediaType,
            String uid,
            String fileName,
            Long expires,
            String signature) {
        if (expires == null || signature == null) {
            throw new ForbiddenException("媒体链接缺少签名");
        }
        MediaServices.MediaDownload media = mediaServices.openSigned(
                mediaType, uid, fileName, expires, signature);
        return ResponseEntity.ok()
                .contentType(media.contentType())
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(media.cacheSeconds())).cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .body(media.resource());
    }
}
