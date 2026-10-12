package com.scaffold.bookkeeping.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

/** 票据文件存储；存储键仅供后端使用，不是公开地址。 */
public interface AttachmentStorage {
    StoredAttachment store(MultipartFile file) throws IOException;

    Path load(String storageKey);

    void delete(String storageKey) throws IOException;

    /** storageKey 存储键，fileSize 字节数，sha256 文件校验值。 */
    record StoredAttachment(String storageKey, long fileSize, String sha256) {
    }
}
