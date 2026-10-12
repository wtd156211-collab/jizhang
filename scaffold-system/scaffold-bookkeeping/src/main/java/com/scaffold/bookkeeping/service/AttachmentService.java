package com.scaffold.bookkeeping.service;

import com.scaffold.bookkeeping.mapper.AttachmentMapper;
import com.scaffold.bookkeeping.mapper.RecordMapper;
import com.scaffold.bookkeeping.storage.AttachmentStorage;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.framework.datascope.DataScope;
import com.scaffold.framework.datascope.DataScopeContext;
import com.scaffold.system.domain.vo.IdResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 私有附件：先确认账目可见和可编辑，再存储文件；事务回滚时清理新文件。 */
@Service
public class AttachmentService {
    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);
    private static final Set<String> EDITABLE = Set.of("DRAFT", "RETURNED", "WITHDRAWN");
    private final AttachmentMapper mapper;
    private final RecordMapper records;
    private final AttachmentStorage storage;
    private final long maxBytes;

    public AttachmentService(AttachmentMapper mapper, RecordMapper records, AttachmentStorage storage,
                             @Value("${bookkeeping.storage.max-bytes:10485760}") long maxBytes) {
        this.mapper = mapper;
        this.records = records;
        this.storage = storage;
        this.maxBytes = maxBytes;
    }

    @DataScope
    @Transactional
    public IdResponse upload(Long recordId, MultipartFile file, boolean primary) {
        requireRecord(recordId, true);
        String mime = validateFile(file);
        Long userId = records.selectUserIdByUsername(username());
        if (userId == null) throw new BusinessException(401, "登录已失效");
        AttachmentStorage.StoredAttachment stored;
        try {
            stored = storage.store(file);
        } catch (IOException ex) {
            throw new BusinessException("票据保存失败，请重试");
        }
        if (stored.fileSize() > maxBytes) {
            cleanup(stored.storageKey());
            throw new BusinessException("票据大小不能超过" + maxBytes / 1024 / 1024 + "MB");
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) cleanup(stored.storageKey());
                }
            });
        }
        try {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("recordId", recordId);
            row.put("originalName", filename(file.getOriginalFilename()));
            row.put("storageKey", stored.storageKey());
            row.put("mimeType", mime);
            row.put("fileSize", stored.fileSize());
            row.put("sha256", stored.sha256());
            row.put("isPrimary", primary ? 1 : 0);
            row.put("uploadedBy", userId);
            if (primary) mapper.clearPrimary(recordId, username());
            mapper.insertAttachment(row, username());
            return new IdResponse(((Number) row.get("id")).longValue());
        } catch (RuntimeException ex) {
            cleanup(stored.storageKey());
            throw ex;
        }
    }

    @DataScope
    public List<Map<String, Object>> list(Long recordId) {
        requireRecord(recordId, false);
        return mapper.selectAttachments(recordId);
    }

    @DataScope
    public Download download(Long id) {
        Map<String, Object> attachment = requireAttachment(id);
        requireRecord(((Number) attachment.get("recordId")).longValue(), false);
        Path path = storage.load(String.valueOf(attachment.get("storageKey")));
        if (!Files.isRegularFile(path)) throw new BusinessException("票据文件不存在");
        return new Download(path, String.valueOf(attachment.get("originalName")), String.valueOf(attachment.get("mimeType")));
    }

    @DataScope
    @Transactional
    public void remove(Long id) {
        Map<String, Object> attachment = requireAttachment(id);
        requireRecord(((Number) attachment.get("recordId")).longValue(), true);
        if (mapper.softDelete(id, username()) == 0) throw new BusinessException("附件不存在或已删除");
        // 软删除元数据，原始文件保留供历史追溯和后续 OCR 任务引用。
    }

    @DataScope
    @Transactional
    public void setPrimary(Long id) {
        Map<String, Object> attachment = requireAttachment(id);
        Long recordId = ((Number) attachment.get("recordId")).longValue();
        requireRecord(recordId, true);
        mapper.clearPrimary(recordId, username());
        if (mapper.setPrimary(id, recordId, username()) == 0) throw new BusinessException("附件不存在或已删除");
    }

    private Map<String, Object> requireRecord(Long recordId, boolean editable) {
        if (recordId == null || recordId < 1) throw new BusinessException("账目 ID 不正确");
        Map<String, Object> record = editable
                ? records.lockRecordForWrite(recordId, DataScopeContext.current())
                : records.selectRecordForWrite(recordId, DataScopeContext.current());
        if (record == null) throw new BusinessException("账目不存在或无权访问");
        if (editable && !EDITABLE.contains(String.valueOf(record.get("status")))) {
            throw new BusinessException("当前账目状态不允许修改附件");
        }
        return record;
    }

    private Map<String, Object> requireAttachment(Long id) {
        if (id == null || id < 1) throw new BusinessException("附件 ID 不正确");
        Map<String, Object> attachment = mapper.selectAttachment(id);
        if (attachment == null) throw new BusinessException("附件不存在或已删除");
        return attachment;
    }

    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > maxBytes) {
            throw new BusinessException("票据不能为空且大小不能超过" + maxBytes / 1024 / 1024 + "MB");
        }
        String name = filename(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        String expected = name.endsWith(".png") ? "image/png"
                : name.endsWith(".jpg") || name.endsWith(".jpeg") ? "image/jpeg"
                : name.endsWith(".pdf") ? "application/pdf" : null;
        if (expected == null || !expected.equals(file.getContentType())) {
            throw new BusinessException("仅支持 PNG、JPEG 和 PDF 票据");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] prefix = input.readNBytes(8);
            boolean valid = switch (expected) {
                case "image/png" -> Arrays.equals(prefix, new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
                case "image/jpeg" -> prefix.length >= 3 && (prefix[0] & 255) == 255 && (prefix[1] & 255) == 216 && (prefix[2] & 255) == 255;
                case "application/pdf" -> prefix.length >= 5 && prefix[0] == '%' && prefix[1] == 'P' && prefix[2] == 'D' && prefix[3] == 'F' && prefix[4] == '-';
                default -> false;
            };
            if (!valid) throw new BusinessException("文件内容与票据类型不匹配");
        } catch (IOException ex) {
            throw new BusinessException("无法读取票据文件");
        }
        return expected;
    }

    private String filename(String value) {
        if (value == null || value.isBlank() || value.length() > 255 || value.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException("票据文件名不正确");
        }
        String name = value.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.isBlank()) throw new BusinessException("票据文件名不正确");
        return name;
    }

    private void cleanup(String key) {
        try {
            storage.delete(key);
        } catch (IOException ex) {
            log.warn("清理未保存的票据失败", ex);
        }
    }

    private String username() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }

    /** path 内部文件路径，originalName 原文件名，mimeType 内容类型。 */
    public record Download(Path path, String originalName, String mimeType) {
    }
}
