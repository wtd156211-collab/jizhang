package com.scaffold.bookkeeping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffold.bookkeeping.domain.dto.RecordWriteRequest;
import com.scaffold.bookkeeping.mapper.*;
import com.scaffold.bookkeeping.storage.AttachmentStorage;
import com.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OcrPermissionTest {
    final OcrTaskMapper tasks = mock(OcrTaskMapper.class);
    final RecordMapper records = mock(RecordMapper.class);
    final AttachmentMapper attachments = mock(AttachmentMapper.class);
    private OcrService service(String granted) {
        return new OcrService(tasks, records, attachments, mock(AttachmentStorage.class), mock(OcrConfigService.class),
                mock(RecordService.class), new ObjectMapper(), (user, permission) -> granted.equals(permission));
    }
    @Test void recognizePermissionAloneCannotReadOriginalOrTaskResults() {
        var service = service("bookkeeping:ocr:recognize");
        assertEquals(403, assertThrows(BusinessException.class, () -> service.recognize(1L,null)).code());
        assertEquals(403, assertThrows(BusinessException.class, () -> service.get(1L)).code());
        assertThrows(BusinessException.class, () -> service.list(1L,null));
        verifyNoInteractions(tasks,records,attachments);
    }
    @Test void attachmentReadAloneCannotConfirmIntoRecord() {
        assertEquals(403, assertThrows(BusinessException.class, () -> service("bookkeeping:attachment:view").confirm(1L,new RecordWriteRequest())).code());
        verifyNoInteractions(tasks,records,attachments);
    }
}
