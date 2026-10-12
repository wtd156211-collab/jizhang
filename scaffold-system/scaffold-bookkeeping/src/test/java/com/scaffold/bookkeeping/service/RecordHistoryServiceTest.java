package com.scaffold.bookkeeping.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffold.bookkeeping.mapper.AttachmentMapper;
import com.scaffold.bookkeeping.mapper.RecordMapper;
import com.scaffold.bookkeeping.mapper.ReviewMapper;
import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.framework.datascope.DataScopeAspect;
import com.scaffold.framework.datascope.DataScopeCriteria;
import com.scaffold.framework.security.PermissionResolver;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecordHistoryServiceTest {
    private final RecordMapper records = mock(RecordMapper.class);
    private final AttachmentMapper attachments = mock(AttachmentMapper.class);
    private final ReviewMapper reviews = mock(ReviewMapper.class);
    private final PermissionResolver permissions = mock(PermissionResolver.class);
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final RecordHistoryService service = new RecordHistoryService(records, attachments, reviews, json, permissions);

    @Test
    void snapshotsFreezeDetailsAndAttachmentMetadataWithoutExposingStorageKeys() throws Exception {
        Map<String, Object> detail = new HashMap<>(Map.of("amount", "100.00", "categoryName", "办公费"));
        when(records.selectDetails(100L)).thenReturn(List.of(detail));
        when(attachments.selectAttachments(100L)).thenReturn(List.of(Map.of("id", 12L, "originalName", "票据.png", "storageKey", "private/path")));
        JsonNode before = service.capture(Map.of("id", 100L, "status", "DRAFT", "version", 2));
        detail.put("amount", "200.00");
        when(records.selectRecordForHistory(eq(100L), any())).thenReturn(Map.of("id", 100L, "status", "PENDING_REVIEW", "version", 3));
        when(reviews.insertVersion(eq(100L), eq(3), eq("SUBMIT"), anyString(), isNull(), eq(7L))).thenReturn(1);

        service.append(100L, 3, "SUBMIT", null, 7L, before);

        var payload = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(reviews).insertVersion(eq(100L), eq(3), eq("SUBMIT"), payload.capture(), isNull(), eq(7L));
        JsonNode snapshot = json.readTree(payload.getValue());
        assertEquals("100.00", snapshot.at("/before/details/0/amount").asText());
        assertEquals("200.00", snapshot.at("/after/details/0/amount").asText());
        assertEquals("DRAFT", snapshot.at("/before/status").asText());
        assertEquals("PENDING_REVIEW", snapshot.at("/after/status").asText());
        assertFalse(snapshot.at("/after/attachments/0").has("storageKey"));
    }

    @Test
    void deletedRecordsKeepQueryableHistoryAndVersionsRespectAttachmentPermission() {
        when(records.selectRecordForHistory(eq(100L), any())).thenReturn(Map.of("id", 100L, "deleted", 1));
        when(reviews.countVersions(100L)).thenReturn(1L);
        when(reviews.selectVersions(100L, 0, 20)).thenReturn(List.of(Map.of(
                "id", 5L, "recordId", 100L, "versionNo", 3, "action", "DELETE", "operatorId", 7L,
                "snapshotJson", "{\"before\":{\"summary\":\"办公费用\",\"attachments\":[{\"id\":12}]},\"after\":{\"deleted\":1,\"attachments\":[{\"id\":12}]}}")));
        var result = service.versions(100L, new PageQuery(1, 20, null, null));
        assertEquals(1, result.total());
        assertEquals(1, result.rows().get(0).after().get("deleted").asInt());
        assertFalse(result.rows().get(0).before().has("attachments"));
        when(permissions.hasPermission("system", "bookkeeping:attachment:view")).thenReturn(true);
        assertEquals(12, service.versions(100L, new PageQuery(1, 20, null, null)).rows().get(0).before().at("/attachments/0/id").asInt());
    }

    @Test
    void historyAndVersionsDenyInvisibleRecordsBeforeReadingSnapshots() {
        when(records.selectRecordForHistory(eq(100L), any())).thenReturn(null);
        AspectJProxyFactory factory = new AspectJProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAspect(new DataScopeAspect(username -> DataScopeCriteria.selfOnly(99L)));
        RecordHistoryService scoped = factory.getProxy();
        assertThrows(BusinessException.class, () -> scoped.reviews(100L, null));
        assertThrows(BusinessException.class, () -> scoped.versions(100L, null));
        verifyNoInteractions(reviews);
    }
}
