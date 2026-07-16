package com.blkn.lr.lr_new_server.dao.impl;

import com.blkn.lr.lr_new_server.exception.FileIOException;
import com.blkn.lr.lr_new_server.exception.FileSizeException;
import com.blkn.lr.lr_new_server.exception.FileTypeException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FileDao 单元测试。
 *
 * <p>FileDao 通过 {@link com.blkn.lr.lr_new_server.config.StaticResourcesConfig#getImageDirPath}
 * / {@code getAudioDirPath} 间接依赖 {@code System.getProperty("user.dir")} —— 写入路径
 * 与运行进程的 cwd 相对。本测试在每个用例之前把 {@code user.dir} 临时指向 JUnit5 提供的
 * {@code @TempDir}，结束后还原，保证 ① 真实文件 IO 路径被覆盖（不光是 null-list 兜底）；
 * ② 不污染开发机工作目录、不与并行用例共享状态。
 *
 * <p>覆盖路径：① saveToFile 正常写入 ② 同名文件覆盖（destFile.exists 走 delete 分支）
 * ③ MultipartFile.transferTo 抛 IOException → 翻成 FileIOException ④ listFiles 返
 * null（已有兜底测试，保留）⑤ getAll{Image,Audio}UrlPaths 真实文件列表 → URL 拼装。
 */
class FileDaoTest {

    private final FileDao fileDao = new FileDao();

    @TempDir
    Path tempDir;

    private String originalUserDir;

    @BeforeEach
    void setUp() {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        // 还原 user.dir，避免污染后续用例 / IDE 开发环境
        if (originalUserDir != null) {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    // ============================================================
    // listFiles 返 null 的兜底（原有测试，保留）
    // ============================================================

    @Test
    void getAllImageUrlPathsShouldReturnEmptyWhenDirMissing() {
        String unknownUid = "no-such-user-" + UUID.randomUUID();
        List<String> paths = fileDao.getAllImageUrlPaths(unknownUid);
        assertNotNull(paths);
        assertTrue(paths.isEmpty());
    }

    @Test
    void getAllAudioUrlPathsShouldReturnEmptyWhenDirMissing() {
        String unknownUid = "no-such-user-" + UUID.randomUUID();
        List<String> paths = fileDao.getAllAudioUrlPaths(unknownUid);
        assertNotNull(paths);
        assertTrue(paths.isEmpty());
    }

    // ============================================================
    // 真实写入路径 —— createImageFile / createAudioFile
    // ============================================================

    @Test
    void createImageFileShouldWriteBytesToImageDirUnderUid() throws Exception {
        String uid = "user-img-1";
        MockMultipartFile mf = new MockMultipartFile(
                "file", "../../pic.png", "image/png", pngBytes());

        File result = fileDao.createImageFile(mf, uid);

        assertTrue(result.getName().matches("[0-9a-f-]{36}\\.png"));
        assertTrue(result.exists(), "目标文件应已写入");
        assertEquals(pngBytes().length, Files.size(result.toPath()), "字节数应与 multipart 一致");
        // 父目录应在 tempDir/images/<uid>
        assertEquals(tempDir.resolve("images").resolve(uid).toFile().getCanonicalPath(),
                result.getParentFile().getCanonicalPath());
    }

    @Test
    void createAudioFileShouldWriteBytesToAudioDirUnderUid() throws Exception {
        String uid = "user-aud-1";
        MockMultipartFile mf = new MockMultipartFile(
                "file", "../voice.wav", "audio/wav", wavBytes());

        File result = fileDao.createAudioFile(mf, uid);

        assertTrue(result.exists());
        assertEquals(wavBytes().length, Files.size(result.toPath()));
        assertTrue(result.getName().matches("[0-9a-f-]{36}\\.wav"));
        assertEquals(tempDir.resolve("audio").resolve(uid).toFile().getCanonicalPath(),
                result.getParentFile().getCanonicalPath());
    }

    @Test
    void createImageFileShouldGenerateUniqueNamesForSameOriginalFilename() {
        String uid = "user-img-2";
        MockMultipartFile first = new MockMultipartFile(
                "file", "x.png", "image/png", pngBytes());
        File firstSaved = fileDao.createImageFile(first, uid);

        MockMultipartFile second = new MockMultipartFile(
                "file", "x.png", "image/png", pngBytes());
        File secondSaved = fileDao.createImageFile(second, uid);

        assertTrue(firstSaved.exists());
        assertTrue(secondSaved.exists());
        assertTrue(!firstSaved.getName().equals(secondSaved.getName()));
    }

    @Test
    void createImageFileShouldThrowFileIoExceptionWhenTransferToFails() {
        // MultipartFile.transferTo 抛 IOException → 应翻成 FileIOException（不直接吐 IOException）
        MultipartFile failing = new MockMultipartFile(
                "file", "boom.png", "image/png", pngBytes()) {
            @Override
            public void transferTo(@org.jetbrains.annotations.NotNull File dest) throws IOException {
                throw new IOException("磁盘满");
            }
        };

        assertThrows(FileIOException.class,
                () -> fileDao.createImageFile(failing, "user-fail"));
    }

    @Test
    void createImageFileShouldRejectSpoofedMimeType() {
        MockMultipartFile fake = new MockMultipartFile(
                "file", "fake.png", "image/png", "not-an-image".getBytes());

        assertThrows(FileTypeException.class,
                () -> fileDao.createImageFile(fake, "user-img-3"));
    }

    @Test
    void createAudioFileShouldRejectImageBytes() {
        MockMultipartFile fake = new MockMultipartFile(
                "file", "fake.wav", "audio/wav", pngBytes());

        assertThrows(FileTypeException.class,
                () -> fileDao.createAudioFile(fake, "user-aud-3"));
    }

    @Test
    void createImageFileShouldRejectOversizedFile() {
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(pngBytes(), 0, oversized, 0, pngBytes().length);
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.png", "image/png", oversized);

        assertThrows(FileSizeException.class,
                () -> fileDao.createImageFile(file, "user-img-large"));
    }

    // ============================================================
    // 真实读取路径 —— getAll{Image,Audio}UrlPaths
    // ============================================================

    @Test
    void getAllImageUrlPathsShouldReturnAllFileNamesUnderUid() throws Exception {
        String uid = "user-img-list";
        // 写两张图
        fileDao.createImageFile(new MockMultipartFile(
                "f", "a.png", "image/png", pngBytes()), uid);
        fileDao.createImageFile(new MockMultipartFile(
                "f", "b.jpg", "image/jpeg", jpegBytes()), uid);

        List<String> urls = fileDao.getAllImageUrlPaths(uid);
        assertEquals(2, urls.size());
        assertTrue(urls.stream().allMatch(path -> path.startsWith("/images/" + uid + "/")));
        assertTrue(urls.stream().anyMatch(path -> path.endsWith(".png")));
        assertTrue(urls.stream().anyMatch(path -> path.endsWith(".jpg")));
    }

    @Test
    void getAllAudioUrlPathsShouldReturnAllFileNamesUnderUid() throws Exception {
        String uid = "user-aud-list";
        fileDao.createAudioFile(new MockMultipartFile(
                "f", "c.wav", "audio/wav", wavBytes()), uid);

        List<String> urls = fileDao.getAllAudioUrlPaths(uid);
        assertEquals(1, urls.size());
        assertTrue(urls.get(0).matches("/audio/" + uid + "/[0-9a-f-]{36}\\.wav"));
    }

    @Test
    void getAllImageUrlPathsShouldReturnEmptyWhenDirExistsButIsEmpty() throws Exception {
        // 区别于 dir 不存在 (null) 的情况：dir 存在但内容为空 → for 循环 0 次
        String uid = "user-img-empty";
        Files.createDirectories(tempDir.resolve("images").resolve(uid));

        assertEquals(List.of(), fileDao.getAllImageUrlPaths(uid));
    }

    private static byte[] pngBytes() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x00
        };
    }

    private static byte[] jpegBytes() {
        return new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46
        };
    }

    private static byte[] wavBytes() {
        return new byte[]{
                0x52, 0x49, 0x46, 0x46, 0x04, 0x00, 0x00, 0x00,
                0x57, 0x41, 0x56, 0x45, 0x66, 0x6D, 0x74, 0x20
        };
    }
}
