package com.blkn.lr.lr_new_server.services;

import com.blkn.lr.lr_new_server.config.StaticResourcesConfig;
import com.blkn.lr.lr_new_server.dao.impl.FileDao;
import com.blkn.lr.lr_new_server.dto.models.media.MediaFileDto;
import com.blkn.lr.lr_new_server.exception.ForbiddenException;
import com.blkn.lr.lr_new_server.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaServices {
    private final FileDao fileDao;
    private final MediaUrlService mediaUrlService;

    public MediaFileDto saveImage(MultipartFile file, String uid) {
        File saved = fileDao.createImageFile(file, uid);
        return toDto(StaticResourcesConfig.IMAGE_DIR, uid, saved.getName());
    }

    public MediaFileDto saveAudio(MultipartFile file, String uid) {
        File saved = fileDao.createAudioFile(file, uid);
        return toDto(StaticResourcesConfig.AUDIO_DIR, uid, saved.getName());
    }

    public List<MediaFileDto> listImages(String uid) {
        return fileDao.getAllImageFileNames(uid).stream()
                .map(fileName -> toDto(StaticResourcesConfig.IMAGE_DIR, uid, fileName))
                .toList();
    }

    public List<MediaFileDto> listAudios(String uid) {
        return fileDao.getAllAudioFileNames(uid).stream()
                .map(fileName -> toDto(StaticResourcesConfig.AUDIO_DIR, uid, fileName))
                .toList();
    }

    public String signUrl(String storageUrl) {
        return mediaUrlService.sign(storageUrl);
    }

    public MediaDownload openSigned(
            String mediaType,
            String uid,
            String fileName,
            long expires,
            String signature) {
        if (!mediaUrlService.isValid(mediaType, uid, fileName, expires, signature)) {
            throw new ForbiddenException("媒体链接无效或已过期");
        }

        Path path = fileDao.findMediaFile(mediaType, uid, fileName)
                .orElseThrow(() -> new NotFoundException("媒体文件不存在"));
        Resource resource = new FileSystemResource(path);
        MediaType contentType = MediaTypeFactory.getMediaType(fileName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        long remainingSeconds = Math.max(0, expires - Instant.now().getEpochSecond());
        return new MediaDownload(resource, contentType, remainingSeconds);
    }

    private MediaFileDto toDto(String mediaType, String uid, String fileName) {
        String storageUrl = StaticResourcesConfig.IMAGE_DIR.equals(mediaType)
                ? StaticResourcesConfig.getImageUrlPath(uid, fileName)
                : StaticResourcesConfig.getAudioUrlPath(uid, fileName);
        return new MediaFileDto(fileName, mediaUrlService.sign(storageUrl));
    }

    public record MediaDownload(Resource resource, MediaType contentType, long cacheSeconds) {
    }
}
