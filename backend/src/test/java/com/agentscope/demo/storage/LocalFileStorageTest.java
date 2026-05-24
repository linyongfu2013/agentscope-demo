package com.agentscope.demo.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void writesAndReadsNormalNestedKey() throws Exception {
        Path root = tempDir.resolve("uploads");
        LocalFileStorage storage = new LocalFileStorage(root.toString());

        String key = storage.put("tenant-a/files/audio.txt", new ByteArrayInputStream("audio".getBytes()));

        assertThat(key).isEqualTo("tenant-a/files/audio.txt");
        assertThat(Files.readString(root.resolve("tenant-a/files/audio.txt"))).isEqualTo("audio");
        assertThat(storage.get(key).readAllBytes()).isEqualTo("audio".getBytes());
    }

    @Test
    void rejectsWriteOutsideRoot() {
        Path root = tempDir.resolve("uploads");
        LocalFileStorage storage = new LocalFileStorage(root.toString());

        assertThatThrownBy(() -> storage.put("../escape.txt", new ByteArrayInputStream("escape".getBytes())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside storage root");

        assertThat(tempDir.resolve("escape.txt")).doesNotExist();
    }

    @Test
    void rejectsReadOutsideRoot() throws Exception {
        Path root = tempDir.resolve("uploads");
        LocalFileStorage storage = new LocalFileStorage(root.toString());
        Files.writeString(tempDir.resolve("escape.txt"), "escape");

        assertThatThrownBy(() -> storage.get("../escape.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside storage root");
    }
}
