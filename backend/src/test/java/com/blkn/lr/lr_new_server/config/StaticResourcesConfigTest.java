package com.blkn.lr.lr_new_server.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StaticResourcesConfigTest {

    @Test
    void shouldBuildStableStoragePaths() {
        assertEquals("/images/user-1/file.png",
                StaticResourcesConfig.getImageUrlPath("user-1", "file.png"));
        assertEquals("/audio/user-1/file.mp3",
                StaticResourcesConfig.getAudioUrlPath("user-1", "file.mp3"));
    }

    @Test
    void shouldRejectTraversalSegments() {
        assertThrows(IllegalArgumentException.class,
                () -> StaticResourcesConfig.getImageUrlPath("../user", "file.png"));
        assertThrows(IllegalArgumentException.class,
                () -> StaticResourcesConfig.getImageUrlPath("user-1", "../file.png"));
        assertThrows(IllegalArgumentException.class,
                () -> StaticResourcesConfig.getMediaFilePath("other", "user-1", "file.png"));
    }
}
