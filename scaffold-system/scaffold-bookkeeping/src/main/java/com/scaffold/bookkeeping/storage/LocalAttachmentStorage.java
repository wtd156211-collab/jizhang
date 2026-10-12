package com.scaffold.bookkeeping.storage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

/** 本地私有票据存储。文件名独立生成，不能使用上传文件名决定路径。 */
@Component
public class LocalAttachmentStorage implements AttachmentStorage {
    private final Path root;

    @Autowired
    public LocalAttachmentStorage(@Value("${bookkeeping.storage.root:./data/bookkeeping}") String root) {
        this(Path.of(root));
    }

    public LocalAttachmentStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public StoredAttachment store(MultipartFile file) throws IOException {
        String key = LocalDate.now() + "/" + UUID.randomUUID() + ".bin";
        Path destination = load(key);
        Files.createDirectories(destination.getParent());
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
        try (InputStream input = new DigestInputStream(file.getInputStream(), digest)) {
            Files.copy(input, destination);
            return new StoredAttachment(key, Files.size(destination), HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | RuntimeException ex) {
            Files.deleteIfExists(destination);
            throw ex;
        }
    }

    @Override
    public Path load(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("\\")
                || storageKey.contains(":") || Path.of(storageKey).isAbsolute()) {
            throw new IllegalArgumentException("存储键不正确");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalArgumentException("存储键超出票据目录");
        }
        return resolved;
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(load(storageKey));
    }
}
