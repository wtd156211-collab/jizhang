package com.scaffold.bookkeeping.service;

import com.scaffold.bookkeeping.domain.dto.RecordDetailWriteRequest;
import com.scaffold.bookkeeping.domain.dto.RecordWriteRequest;
import com.scaffold.bookkeeping.mapper.RecordMapper;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.framework.datascope.DataScopeCriteria;
import com.scaffold.framework.datascope.DataScopeAspect;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class RecordServiceTest {

    @Mock
    private RecordMapper mapper;

    @Mock
    private RecordHistoryService history;

    @InjectMocks
    private RecordService service;

    @Test
    void createsDraftWhenDetailTotalMatches() {
        when(mapper.selectUserIdByUsername("system")).thenReturn(7L);
        when(mapper.countActiveCategories(List.of(3L), "EXPENSE")).thenReturn(1L);
        when(mapper.countActivePaymentMethod(1L)).thenReturn(1L);
        when(mapper.countActiveFundAccount(1L)).thenReturn(1L);
        when(mapper.countActiveDept(5L)).thenReturn(1L);
        when(mapper.insertRecord(any(), eq("system"))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = invocation.getArgument(0);
            row.put("id", 100L);
            return 1;
        });
        RecordWriteRequest request = requestWithAmount("100.00");

        var result = service.create(request);

        assertEquals(100L, result.id());
        verify(history).append(eq(100L), eq(0), eq("CREATE"), isNull(), eq(7L), isNull());
    }

    @Test
    void editingReturnedRecordKeepsStatusAndAppendsTheNextVersion() {
        when(mapper.selectUserIdByUsername("system")).thenReturn(7L);
        when(mapper.lockRecordForWrite(eq(100L), any())).thenReturn(recordRow("RETURNED", 2));
        when(mapper.countActiveCategories(List.of(3L), "EXPENSE")).thenReturn(1L);
        when(mapper.countActivePaymentMethod(1L)).thenReturn(1L);
        when(mapper.countActiveFundAccount(1L)).thenReturn(1L);
        when(mapper.countActiveDept(5L)).thenReturn(1L);
        when(mapper.updateRecord(any(), eq(100L), eq(2), eq("system"))).thenReturn(1);
        var request = requestWithAmount("100.00");
        request.setVersion(2);

        service.update(100L, request);

        verify(history).capture(any());
        verify(history).append(eq(100L), eq(3), eq("UPDATE"), isNull(), eq(7L), isNull());
    }

    @Test
    void deletingDraftRetainsDetailsAndAppendsTheNextVersion() {
        when(mapper.selectUserIdByUsername("system")).thenReturn(7L);
        when(mapper.lockRecordForWrite(eq(100L), any())).thenReturn(recordRow("DRAFT", 2));
        when(mapper.softDeleteRecord(100L, "system")).thenReturn(1);

        service.delete(100L);

        verify(mapper, never()).deleteDetails(anyLong());
        verify(history).capture(any());
        verify(history).append(eq(100L), eq(3), eq("DELETE"), isNull(), eq(7L), isNull());
    }

    @Test
    void postedPendingAndVoidedRecordsCannotBeEditedOrDeleted() {
        when(mapper.selectUserIdByUsername("system")).thenReturn(7L);
        var request = requestWithAmount("100.00");
        request.setVersion(2);
        for (String status : List.of("POSTED", "PENDING_REVIEW", "VOIDED")) {
            when(mapper.lockRecordForWrite(eq(100L), any())).thenReturn(recordRow(status, 2));
            assertThrows(BusinessException.class, () -> service.update(100L, request));
            assertThrows(BusinessException.class, () -> service.delete(100L));
        }
        verify(history, never()).append(anyLong(), anyInt(), anyString(), any(), anyLong(), any());
    }

    @Test
    void rejectsWhenDetailTotalDoesNotMatchHeader() {
        when(mapper.selectUserIdByUsername("system")).thenReturn(7L);
        when(mapper.countActiveDept(5L)).thenReturn(1L);
        RecordWriteRequest request = requestWithAmount("120.00");

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(request));

        assertEquals("账目总金额必须等于明细金额合计", error.getMessage());
    }

    @Test
    void reportsOptimisticLockConflictWhenUpdateAffectsNoRow() {
        when(mapper.selectUserIdByUsername("system")).thenReturn(7L);
        when(mapper.lockRecordForWrite(eq(100L), any(DataScopeCriteria.class))).thenReturn(recordRow("DRAFT", 2));
        when(mapper.countActiveCategories(List.of(3L), "EXPENSE")).thenReturn(1L);
        when(mapper.countActivePaymentMethod(1L)).thenReturn(1L);
        when(mapper.countActiveFundAccount(1L)).thenReturn(1L);
        when(mapper.countActiveDept(5L)).thenReturn(1L);
        when(mapper.updateRecord(any(), eq(100L), eq(2), eq("system"))).thenReturn(0);
        RecordWriteRequest request = requestWithAmount("100.00");
        request.setVersion(2);

        BusinessException error = assertThrows(BusinessException.class, () -> service.update(100L, request));

        assertEquals("账目已被其他人修改，请刷新后重试", error.getMessage());
    }

    @Test
    void updateResolvesDataScopeBeforeLookingUpOtherUsersRecord() {
        when(mapper.selectUserIdByUsername("system")).thenReturn(7L);
        when(mapper.lockRecordForWrite(eq(100L), any(DataScopeCriteria.class)))
                .thenAnswer(invocation -> {
                    DataScopeCriteria scope = invocation.getArgument(1);
                    return scope.isAllScope() ? recordRow("DRAFT", 2) : null;
                });
        AspectJProxyFactory factory = new AspectJProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAspect(new DataScopeAspect(username -> DataScopeCriteria.selfOnly(7L)));
        RecordService scopedService = factory.getProxy();
        RecordWriteRequest request = requestWithAmount("100.00");
        request.setVersion(2);

        BusinessException error = assertThrows(BusinessException.class, () -> scopedService.update(100L, request));
        assertEquals("账目不存在或无权访问", error.getMessage());
    }

    @Test
    void rejectsMissingRecordTypeWithBusinessError() {
        when(mapper.selectUserIdByUsername("system")).thenReturn(7L);
        RecordWriteRequest request = requestWithAmount("100.00");
        request.setRecordType(null);

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(request));
        assertEquals("账目类型只能为收入或支出", error.getMessage());
    }

    @Test
    void rejectsAmountsBeyondDatabasePrecision() {
        when(mapper.selectUserIdByUsername("system")).thenReturn(7L);
        RecordWriteRequest request = requestWithAmount("10000000000000000.00");
        request.getDetails().get(0).setAmount(request.getTotalAmount());

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(request));
        assertEquals("金额不能超过9999999999999999.99", error.getMessage());
    }

    @Test
    void rejectsNonexistentHandler() {
        when(mapper.selectUserIdByUsername("system")).thenReturn(7L);
        RecordWriteRequest request = requestWithAmount("100.00");
        request.getDetails().get(0).setDeptId(null);
        request.getDetails().get(0).setHandlerId(999L);
        when(mapper.countActiveHandler(999L)).thenReturn(0L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.create(request));
        assertEquals("明细经办人不存在或已停用", error.getMessage());
    }

    @Test
    void createCannotAssignRecordToDepartmentOutsideUserScope() {
        when(mapper.selectUserIdByUsername("system")).thenReturn(7L);
        when(mapper.countActiveDept(99L)).thenReturn(1L);
        when(mapper.selectUserDeptIdByUsername("system")).thenReturn(5L);
        AspectJProxyFactory factory = new AspectJProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAspect(new DataScopeAspect(username -> DataScopeCriteria.selfOnly(7L)));
        RecordService scopedService = factory.getProxy();
        RecordWriteRequest request = requestWithAmount("100.00");
        request.setDeptId(99L);

        BusinessException error = assertThrows(BusinessException.class, () -> scopedService.create(request));
        assertEquals("无权将账目归属到该部门", error.getMessage());
    }

    private RecordWriteRequest requestWithAmount(String total) {
        RecordWriteRequest request = new RecordWriteRequest();
        request.setRecordType("EXPENSE");
        request.setSummary("采购办公用品");
        request.setOccurredDate(LocalDate.of(2026, 10, 12));
        request.setTotalAmount(new BigDecimal(total));
        request.setPaymentMethodId(1L);
        request.setFundAccountId(1L);
        RecordDetailWriteRequest detail = new RecordDetailWriteRequest();
        detail.setCategoryId(3L);
        detail.setAmount(new BigDecimal("100.00"));
        detail.setDeptId(5L);
        request.setDetails(List.of(detail));
        return request;
    }

    private Map<String, Object> recordRow(String status, int version) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 100L);
        row.put("status", status);
        row.put("version", version);
        row.put("createdBy", 7L);
        return row;
    }
}
