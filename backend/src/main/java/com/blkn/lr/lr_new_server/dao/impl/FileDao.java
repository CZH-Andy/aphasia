package com.blkn.lr.lr_new_server.dao.impl;

import com.blkn.lr.lr_new_server.config.StaticResourcesConfig;
import com.blkn.lr.lr_new_server.exception.FileEmptyException;
import com.blkn.lr.lr_new_server.exception.FileIOException;
import com.blkn.lr.lr_new_server.exception.FileSizeException;
import com.blkn.lr.lr_new_server.exception.FileTypeException;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Repository;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class FileDao {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private File saveToFile(String directoryPath, MultipartFile file, String extension) {
        if (file.isEmpty()) {
            throw new FileEmptyException("上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileSizeException("单个文件不能超过10MB");
        }

        try {
            Path directory = Path.of(directoryPath).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path destination = directory.resolve(UUID.randomUUID() + "." + extension).normalize();
            if (!destination.startsWith(directory)) {
                throw new FileIOException("上传文件路径非法");
            }
            File destFile = destination.toFile();
            file.transferTo(destFile);
            return destFile;
        } catch (IOException e) {
            throw new FileIOException("保存上传文件失败", e);
        }
    }

    public File createImageFile(MultipartFile file, String uid) {
        return saveToFile(StaticResourcesConfig.getImageDirPath(uid), file, detectImageExtension(file));
    }

    public File createAudioFile(MultipartFile file, String uid) {
        return saveToFile(StaticResourcesConfig.getAudioDirPath(uid), file, detectAudioExtension(file));
    }

    public List<String> getAllImageUrlPaths(String uid) {
        String dirPath = StaticResourcesConfig.getImageDirPath(uid);
        return getFileNames(dirPath).stream().map(e -> StaticResourcesConfig.getImageUrlPath(uid, e)).toList();
    }

    public List<String> getAllAudioUrlPaths(String uid) {
        String dirPath = StaticResourcesConfig.getAudioDirPath(uid);
        return getFileNames(dirPath).stream().map(e -> StaticResourcesConfig.getAudioUrlPath(uid, e)).toList();
    }

    @NotNull
    private List<String> getFileNames(String dirPath) {
        File dir = new File(dirPath);
        File[] files = dir.listFiles();

        List<String> fileNames = new ArrayList<>();
        if (files == null) {
            // 目录不存在或读取失败：该用户尚无文件，返回空列表
            return fileNames;
        }
        for (File f : files) {
            if (f.isFile()) {
                fileNames.add(f.getName());
            }
        }

        fileNames.sort(String::compareTo);
        return fileNames;
    }

    private String detectImageExtension(MultipartFile file) {
        byte[] header = readHeader(file);
        if (startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "png";
        }
        if (startsWith(header, 0xFF, 0xD8, 0xFF)) {
            return "jpg";
        }
        if (startsWithAscii(header, "GIF87a") || startsWithAscii(header, "GIF89a")) {
            return "gif";
        }
        if (startsWithAscii(header, "RIFF") && asciiAt(header, 8, "WEBP")) {
            return "webp";
        }
        throw new FileTypeException("文件内容不是受支持的图片（PNG/JPEG/GIF/WebP）");
    }

    private String detectAudioExtension(MultipartFile file) {
        byte[] header = readHeader(file);
        if (startsWithAscii(header, "RIFF") && asciiAt(header, 8, "WAVE")) {
            return "wav";
        }
        if (startsWithAscii(header, "ID3")
                || (header.length >= 2 && (header[0] & 0xFF) == 0xFF
                && ((header[1] & 0xE0) == 0xE0))) {
            return "mp3";
        }
        if (startsWithAscii(header, "OggS")) {
            return "ogg";
        }
        if (startsWith(header, 0x1A, 0x45, 0xDF, 0xA3)) {
            return "webm";
        }
        if (asciiAt(header, 4, "ftyp")) {
            return "m4a";
        }
        throw new FileTypeException("文件内容不是受支持的音频（WAV/MP3/OGG/WebM/M4A）");
    }

    private byte[] readHeader(MultipartFile file) {
        if (file.isEmpty()) {
            throw new FileEmptyException("上传文件不能为空");
        }
        try (InputStream input = file.getInputStream()) {
            return input.readNBytes(16);
        } catch (IOException e) {
            throw new FileIOException("读取上传文件失败", e);
        }
    }

    private boolean startsWith(byte[] bytes, int... expected) {
        if (bytes.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean startsWithAscii(byte[] bytes, String expected) {
        return asciiAt(bytes, 0, expected);
    }

    private boolean asciiAt(byte[] bytes, int offset, String expected) {
        if (bytes.length < offset + expected.length()) {
            return false;
        }
        for (int i = 0; i < expected.length(); i++) {
            if ((char) bytes[offset + i] != expected.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
