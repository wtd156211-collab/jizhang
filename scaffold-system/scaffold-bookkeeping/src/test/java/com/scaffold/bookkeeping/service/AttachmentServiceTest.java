package com.scaffold.bookkeeping.service;

import com.scaffold.bookkeeping.mapper.AttachmentMapper;
import com.scaffold.bookkeeping.mapper.RecordMapper;
import com.scaffold.bookkeeping.storage.AttachmentStorage;
import com.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.scaffold.bookkeeping.storage.LocalAttachmentStorage;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {
    @Mock private AttachmentMapper mapper;
    @Mock private RecordMapper records;
    @Mock private AttachmentStorage storage;
    private AttachmentService service;
    @TempDir Path directory;

    @AfterEach
    void cleanupSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @BeforeEach
    void setup() {
        service = new AttachmentService(mapper, records, storage, 1024);
    }

    @Test
    void refusesUploadToPostedRecord() {
        when(records.lockRecordForWrite(eq(1L), any())).thenReturn(Map.of("status", "POSTED"));
        var file = new MockMultipartFile("file", "receipt.png", "image/png", new byte[]{1});
        assertEquals("当前账目状态不允许修改附件", assertThrows(BusinessException.class, () -> service.upload(1L, file, false)).getMessage());
    }

    @Test
    void refusesMetadataForInvisibleRecord() {
        when(records.selectRecordForWrite(eq(1L), any())).thenReturn(null);
        assertEquals("账目不存在或无权访问", assertThrows(BusinessException.class, () -> service.list(1L)).getMessage());
    }

    @Test
    void refusesMislabeledFileBeforeStorage() {
        when(records.lockRecordForWrite(eq(1L), any())).thenReturn(Map.of("status", "DRAFT"));
        var file = new MockMultipartFile("file", "receipt.png", "image/png", "<script>alert(1)</script>".getBytes());
        assertEquals("文件内容与票据类型不匹配", assertThrows(BusinessException.class, () -> service.upload(1L, file, false)).getMessage());
    }

    @Test
    void refusesFileExceedingConfiguredSize() {
        when(records.lockRecordForWrite(eq(1L), any())).thenReturn(Map.of("status", "DRAFT"));
        var file = new MockMultipartFile("file", "receipt.png", "image/png", new byte[1025]);
        assertTrue(assertThrows(BusinessException.class, () -> service.upload(1L, file, false)).getMessage().contains("大小不能超过"));
    }

    @Test
    void savesOriginalBytesAndDownloadsThemAfterCheckingRecordVisibility() throws Exception {
        var local = new LocalAttachmentStorage(directory);
        service = new AttachmentService(mapper, records, local, 1024);
        when(records.lockRecordForWrite(eq(1L), any())).thenReturn(Map.of("status", "DRAFT"));
        when(records.selectUserIdByUsername("system")).thenReturn(7L);
        Map<String, Object> persisted = new HashMap<>();
        when(mapper.insertAttachment(any(), eq("system"))).thenAnswer(invocation -> {
            Map<String, Object> row = invocation.getArgument(0);
            row.put("id", 12L);
            persisted.putAll(row);
            return 1;
        });
        byte[] original = "%PDF-1.4\noriginal-receipt".getBytes(StandardCharsets.UTF_8);
        var file = new MockMultipartFile("file", "C:\\upload\\票据.pdf", "application/pdf", original);

        assertEquals(12L, service.upload(1L, file, true).id());
        assertEquals("票据.pdf", persisted.get("originalName"));
        assertEquals(1, persisted.get("isPrimary"));
        when(mapper.selectAttachment(12L)).thenReturn(persisted);
        when(records.selectRecordForWrite(eq(1L), any())).thenReturn(Map.of("status", "DRAFT"));
        var download = service.download(12L);
        assertArrayEquals(original, Files.readAllBytes(download.path()));
        assertEquals("application/pdf", download.mimeType());
    }

    @Test
    void removesNewFileWhenDatabaseTransactionRollsBack() throws Exception {
        var local = new LocalAttachmentStorage(directory);
        service = new AttachmentService(mapper, records, local, 1024);
        when(records.lockRecordForWrite(eq(1L), any())).thenReturn(Map.of("status", "DRAFT"));
        when(records.selectUserIdByUsername("system")).thenReturn(7L);
        Map<String, Object> persisted = new HashMap<>();
        when(mapper.insertAttachment(any(), eq("system"))).thenAnswer(invocation -> {
            Map<String, Object> row = invocation.getArgument(0);
            row.put("id", 12L);
            persisted.putAll(row);
            return 1;
        });
        TransactionSynchronizationManager.initSynchronization();
        var file = new MockMultipartFile("file", "receipt.pdf", "application/pdf", "%PDF-original".getBytes());
        service.upload(1L, file, false);
        Path saved = local.load((String) persisted.get("storageKey"));
        assertTrue(Files.exists(saved));

        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertFalse(Files.exists(saved));
    }

    @Test
    void refusesDownloadForInvisibleRecord() {
        when(mapper.selectAttachment(12L)).thenReturn(Map.of("recordId", 1L));
        when(records.selectRecordForWrite(eq(1L), any())).thenReturn(null);
        assertEquals("账目不存在或无权访问", assertThrows(BusinessException.class, () -> service.download(12L)).getMessage());
    }

    @Test
    void refusesDeletionAfterSubmit() {
        when(mapper.selectAttachment(12L)).thenReturn(Map.of("recordId", 1L));
        when(records.lockRecordForWrite(eq(1L), any())).thenReturn(Map.of("status", "PENDING_REVIEW"));
        assertEquals("当前账目状态不允许修改附件", assertThrows(BusinessException.class, () -> service.remove(12L)).getMessage());
    }

    @Test
    void checksActualStoredSizeEvenIfReportedUploadSizeIsSmaller() throws Exception {
        var local = new LocalAttachmentStorage(directory);
        service = new AttachmentService(mapper, records, local, 10);
        when(records.lockRecordForWrite(eq(1L), any())).thenReturn(Map.of("status", "DRAFT"));
        when(records.selectUserIdByUsername("system")).thenReturn(7L);
        var file = new MockMultipartFile("file", "receipt.pdf", "application/pdf", "%PDF-original-receipt".getBytes()) {
            @Override public long getSize() { return 8; }
        };

        assertTrue(assertThrows(BusinessException.class, () -> service.upload(1L, file, false)).getMessage().contains("大小不能超过"));
        try (var entries = Files.walk(directory)) {
            assertEquals(0, entries.filter(Files::isRegularFile).count());
        }
    }
}
