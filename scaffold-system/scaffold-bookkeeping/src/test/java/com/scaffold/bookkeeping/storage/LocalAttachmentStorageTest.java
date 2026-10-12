package com.scaffold.bookkeeping.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalAttachmentStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storesFileUnderGeneratedKeyAndCanLoadItAgain() throws Exception {
        LocalAttachmentStorage storage = new LocalAttachmentStorage(tempDir);
        MockMultipartFile file = new MockMultipartFile("file", "receipt.png", "image/png", new byte[]{1, 2, 3, 4});

        AttachmentStorage.StoredAttachment stored = storage.store(file);

        assertFalse(stored.storageKey().contains(".."));
        assertTrue(Files.exists(storage.load(stored.storageKey())));
        assertArrayEquals(new byte[]{1, 2, 3, 4}, Files.readAllBytes(storage.load(stored.storageKey())));
        storage.delete(stored.storageKey());
        assertFalse(Files.exists(storage.load(stored.storageKey())));
    }

    @Test
    void rejectsStorageKeyOutsideRoot() {
        LocalAttachmentStorage storage = new LocalAttachmentStorage(tempDir);
        assertThrows(IllegalArgumentException.class, () -> storage.load("../outside.png"));
        assertThrows(IllegalArgumentException.class, () -> storage.load("C:/outside.png"));
    }
}
