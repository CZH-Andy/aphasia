package com.blkn.lr.lr_new_server.controllers;

import com.blkn.lr.lr_new_server.dto.models.media.MediaFileDto;
import com.blkn.lr.lr_new_server.interceptor.RequireRole;
import com.blkn.lr.lr_new_server.services.MediaServices;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequireRole({1, 2})
@RequiredArgsConstructor
public class FileController {
    private final MediaServices mediaServices;

    @PostMapping("/image")
    MediaFileDto uploadImages(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        String uid = (String) request.getAttribute("uid");
        return mediaServices.saveImage(file, uid);
    }

    @PostMapping("/audio")
    MediaFileDto uploadAudio(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        String uid = (String) request.getAttribute("uid");
        return mediaServices.saveAudio(file, uid);
    }

    @GetMapping("/images")
    List<MediaFileDto> getAllImageInfo(HttpServletRequest request) {
        String uid = (String) request.getAttribute("uid");
        return mediaServices.listImages(uid);
    }

    @GetMapping("/audios")
    List<MediaFileDto> getAllAudioInfo(HttpServletRequest request) {
        String uid = (String) request.getAttribute("uid");
        return mediaServices.listAudios(uid);
    }
}
