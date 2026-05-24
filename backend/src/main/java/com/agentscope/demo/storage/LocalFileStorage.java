package com.agentscope.demo.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(@Value("${app.storage.local-root:./data/uploads}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    @Override
    public String put(String key, InputStream inputStream) throws IOException {
        Path target = resolveKey(key);
        Files.createDirectories(target.getParent());
        Files.copy(inputStream, target);
        return key;
    }

    @Override
    public InputStream get(String key) throws IOException {
        return Files.newInputStream(resolveKey(key));
    }

    private Path resolveKey(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("storage key resolves outside storage root");
        }
        return target;
    }
}
