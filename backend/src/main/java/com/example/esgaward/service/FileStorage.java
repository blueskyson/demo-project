package com.example.esgaward.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Stores uploaded file bytes on the local filesystem under {@code app.storage.dir}, keyed by a
 * random UUID so user-supplied filenames never touch the filesystem.
 */
@Component
public class FileStorage {

    private final Path root;

    public FileStorage(@Value("${app.storage.dir}") String dir) throws IOException {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    /**
     * Writes the stream and returns its storage key. When called inside a transaction, the file
     * is removed again if that transaction rolls back.
     */
    public String store(InputStream content) {
        String key = UUID.randomUUID().toString();
        try {
            Files.copy(content, resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file", e);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        delete(key);
                    }
                }
            });
        }
        return key;
    }

    public Resource load(String key) {
        return new PathResource(resolve(key));
    }

    /** Deletes the file once the surrounding transaction commits (immediately if there is none). */
    public void deleteAfterCommit(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            delete(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                delete(key);
            }
        });
    }

    private void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete file " + key, e);
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }
}
