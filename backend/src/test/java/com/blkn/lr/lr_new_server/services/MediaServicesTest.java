package com.blkn.lr.lr_new_server.services;

import com.blkn.lr.lr_new_server.dao.impl.FileDao;
import com.blkn.lr.lr_new_server.exception.ForbiddenException;
import com.blkn.lr.lr_new_server.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaServicesTest {
    private FileDao fileDao;
    private MediaUrlService urlService;
    private MediaServices services;

    @BeforeEach
    void setUp() {
        fileDao = mock(FileDao.class);
        urlService = mock(MediaUrlService.class);
        services = new MediaServices(fileDao, urlService);
    }

    @Test
    void shouldRejectInvalidSignatureBeforeTouchingDisk() {
        when(urlService.isValid("images", "user-1", "a.png", 123L, "bad")).thenReturn(false);

        assertThrows(ForbiddenException.class,
                () -> services.openSigned("images", "user-1", "a.png", 123L, "bad"));
        verify(fileDao, never()).findMediaFile("images", "user-1", "a.png");
    }

    @Test
    void shouldReturnNotFoundOnlyAfterValidSignature() {
        when(urlService.isValid("audio", "user-1", "a.mp3", 123L, "ok")).thenReturn(true);
        when(fileDao.findMediaFile("audio", "user-1", "a.mp3")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> services.openSigned("audio", "user-1", "a.mp3", 123L, "ok"));
    }

    @Test
    void shouldOpenExistingMediaWithDetectedContentType() throws Exception {
        Path file = Files.createTempFile("media-service-", ".png");
        try {
            when(urlService.isValid("images", "user-1", "a.png", Long.MAX_VALUE, "ok"))
                    .thenReturn(true);
            when(fileDao.findMediaFile("images", "user-1", "a.png"))
                    .thenReturn(Optional.of(file));

            MediaServices.MediaDownload media = services.openSigned(
                    "images", "user-1", "a.png", Long.MAX_VALUE, "ok");

            assertEquals("image/png", media.contentType().toString());
            assertEquals(file.toFile(), media.resource().getFile());
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
