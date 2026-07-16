package com.blkn.lr.lr_new_server.config;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 媒体存储路径工具。
 *
 * <p>上传目录不再通过 Spring 静态资源处理器直接暴露。所有下载必须经过
 * {@code MediaController} 的短期签名校验。
 */
public final class StaticResourcesConfig {
    public static final String IMAGE_DIR = "images";
    public static final String AUDIO_DIR = "audio";
    private static final Pattern SAFE_PATH_SEGMENT = Pattern.compile("[A-Za-z0-9_-]{1,128}");
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("[A-Za-z0-9._-]{1,255}");
    private static final Set<String> MEDIA_TYPES = Set.of(IMAGE_DIR, AUDIO_DIR);

    private StaticResourcesConfig() {
    }

    public static String getImageUrlPath(String uid, String fileName) {
        validateUid(uid);
        validateFileName(fileName);
        return "/" + StaticResourcesConfig.IMAGE_DIR + "/" + uid + "/" + fileName;
    }

    public static String getImageDirPath(String uid) {
        return getMediaDirPath(StaticResourcesConfig.IMAGE_DIR, uid);
    }

    public static String getAudioUrlPath(String uid, String fileName) {
        validateUid(uid);
        validateFileName(fileName);
        return "/" + StaticResourcesConfig.AUDIO_DIR + "/" + uid + "/" + fileName;
    }

    public static String getAudioDirPath(String uid) {
        return getMediaDirPath(StaticResourcesConfig.AUDIO_DIR, uid);
    }

    public static Path getMediaFilePath(String mediaType, String uid, String fileName) {
        validateMediaType(mediaType);
        validateUid(uid);
        validateFileName(fileName);

        Path directory = Path.of(getMediaDirPath(mediaType, uid)).toAbsolutePath().normalize();
        Path file = directory.resolve(fileName).normalize();
        if (!file.startsWith(directory)) {
            throw new IllegalArgumentException("非法媒体文件路径");
        }
        return file;
    }

    private static String getMediaDirPath(String mediaType, String uid) {
        validateMediaType(mediaType);
        validateUid(uid);
        String configuredRoot = System.getenv("MEDIA_ROOT");
        String root = configuredRoot == null || configuredRoot.isBlank()
                ? System.getProperty("user.dir")
                : configuredRoot;
        return Path.of(root, mediaType, uid).toString() + File.separator;
    }

    private static void validateMediaType(String mediaType) {
        if (!MEDIA_TYPES.contains(mediaType)) {
            throw new IllegalArgumentException("非法媒体类型");
        }
    }

    private static void validateUid(String uid) {
        if (uid == null || !SAFE_PATH_SEGMENT.matcher(uid).matches()) {
            throw new IllegalArgumentException("非法用户标识");
        }
    }

    private static void validateFileName(String fileName) {
        if (fileName == null
                || !SAFE_FILE_NAME.matcher(fileName).matches()
                || ".".equals(fileName)
                || "..".equals(fileName)) {
            throw new IllegalArgumentException("非法媒体文件名");
        }
    }
}
