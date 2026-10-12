package com.scaffold.bookkeeping.service;

import com.scaffold.bookkeeping.domain.dto.ReviewActionRequest;
import com.scaffold.bookkeeping.domain.ReviewAction;
import com.scaffold.bookkeeping.mapper.RecordMapper;
import com.scaffold.bookkeeping.mapper.ReviewMapper;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.framework.datascope.DataScopeAspect;
import com.scaffold.framework.datascope.DataScopeCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Mapper 是唯一外部边界；真实服务校验状态、版本、数据范围和历史写入顺序。 */
class ReviewServiceTest {
    private RecordMapper records;
    private ReviewMapper reviews;
    private RecordHistoryService history;
    private ReviewService service;
    private Map<String, Object> row;

    @BeforeEach
    void setup() {
        records = mock(RecordMapper.class);
        reviews = mock(ReviewMapper.class);
        history = mock(RecordHistoryService.class);
        service = new ReviewService(records, reviews, history, mock(RecordService.class));
        row = new HashMap<>(Map.of("id", 100L, "status", "DRAFT", "version", 2, "createdBy", 7L));
        when(records.selectUserIdByUsername("system")).thenReturn(7L);
        when(records.lockRecordForWrite(eq(100L), any())).thenAnswer(i -> new HashMap<>(row));
        when(reviews.updateStatus(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenAnswer(i -> {
            if (((Integer) i.getArgument(1)).intValue() != (Integer) row.get("version")) return 0;
            row.put("status", i.getArgument(3));
            row.put("version", (Integer) row.get("version") + 1);
            return 1;
        });
        when(reviews.insertReview(anyLong(), anyString(), anyString(), anyString(), nullable(String.class), anyLong()))
                .thenReturn(1);
    }

    static Stream<Object[]> stateMatrix() {
        Map<String, String> allowed = Map.of(
                "DRAFT:SUBMIT", "PENDING_REVIEW", "RETURNED:SUBMIT", "PENDING_REVIEW",
                "WITHDRAWN:SUBMIT", "PENDING_REVIEW", "PENDING_REVIEW:APPROVE", "POSTED",
                "PENDING_REVIEW:RETURN", "RETURNED", "POSTED:WITHDRAW", "WITHDRAWN", "POSTED:VOID", "VOIDED");
        return Stream.of("DRAFT", "PENDING_REVIEW", "RETURNED", "POSTED", "WITHDRAWN", "VOIDED")
                .flatMap(status -> Stream.of("SUBMIT", "APPROVE", "RETURN", "WITHDRAW", "VOID")
                        .map(action -> new Object[]{status, action, allowed.get(status + ":" + action)}));
    }

    @ParameterizedTest(name = "{0} / {1} → {2}")
    @MethodSource("stateMatrix")
    void enforcesEveryStateAndAction(String status, String action, String expected) {
        row.put("status", status);
        if (expected == null) {
            assertThrows(BusinessException.class, () -> service.act(100L, ReviewAction.valueOf(action), request(2, "原因")));
            assertEquals(status, row.get("status"));
            verifyNoInteractions(history);
            verify(reviews, never()).insertReview(anyLong(), anyString(), anyString(), anyString(), any(), anyLong());
        } else {
            service.act(100L, ReviewAction.valueOf(action), request(2, "  原因  "));
            assertEquals(expected, row.get("status"));
            assertEquals(3, row.get("version"));
            verify(reviews).insertReview(100L, action, status, expected, "原因", 7L);
            verify(history).append(eq(100L), eq(3), eq(action), eq("原因"), eq(7L), any());
        }
    }

    @Test
    void returnAndWithdrawCanBeSubmittedAgainButVoidIsTerminal() {
        service.act(100L, ReviewAction.SUBMIT, request(2, null));
        service.act(100L, ReviewAction.RETURN, request(3, "补充票据"));
        service.act(100L, ReviewAction.SUBMIT, request(4, null));
        service.act(100L, ReviewAction.APPROVE, request(5, null));
        service.act(100L, ReviewAction.WITHDRAW, request(6, "修订金额"));
        service.act(100L, ReviewAction.SUBMIT, request(7, null));
        service.act(100L, ReviewAction.APPROVE, request(8, null));
        service.act(100L, ReviewAction.VOID, request(9, "重复记账"));
        assertEquals("VOIDED", row.get("status"));
        assertEquals(10, row.get("version"));
        assertThrows(BusinessException.class, () -> service.act(100L, ReviewAction.SUBMIT, request(10, null)));
    }

    @Test
    void mandatoryReasonsCannotBeBlankAndOptionalReasonsAreLengthLimited() {
        for (String action : new String[]{"RETURN", "WITHDRAW", "VOID"}) {
            row.put("status", action.equals("RETURN") ? "PENDING_REVIEW" : "POSTED");
            assertThrows(BusinessException.class, () -> service.act(100L, ReviewAction.valueOf(action), request(2, " \t ")));
        }
        row.put("status", "DRAFT");
        assertThrows(BusinessException.class, () -> service.act(100L, ReviewAction.SUBMIT, request(2, "字".repeat(1001))));
        verifyNoInteractions(history);
    }

    @Test
    void rejectsMissingOrStaleVersionsAndDuplicateActionWithoutWritingHistory() {
        assertThrows(BusinessException.class, () -> service.act(100L, ReviewAction.SUBMIT, request(null, null)));
        assertThrows(BusinessException.class, () -> service.act(100L, ReviewAction.SUBMIT, request(-1, null)));
        BusinessException stale = assertThrows(BusinessException.class,
                () -> service.act(100L, ReviewAction.SUBMIT, request(1, null)));
        assertEquals(409, stale.code());
        service.act(100L, ReviewAction.SUBMIT, request(2, null));
        assertThrows(BusinessException.class, () -> service.act(100L, ReviewAction.SUBMIT, request(2, null)));
        verify(history, times(1)).append(anyLong(), anyInt(), anyString(), any(), anyLong(), any());
    }

    @Test
    void failedConditionalUpdateDoesNotWriteHistory() {
        when(reviews.updateStatus(anyLong(), anyInt(), anyString(), anyString(), anyString())).thenReturn(0);
        BusinessException conflict = assertThrows(BusinessException.class,
                () -> service.act(100L, ReviewAction.SUBMIT, request(2, null)));
        assertEquals(409, conflict.code());
        verify(history, never()).append(anyLong(), anyInt(), anyString(), any(), anyLong(), any());
        verify(reviews, never()).insertReview(anyLong(), anyString(), anyString(), anyString(), any(), anyLong());
    }

    @Test
    void dataScopePreventsReviewingAnotherUsersRecord() {
        when(records.lockRecordForWrite(eq(100L), any())).thenAnswer(i -> {
            DataScopeCriteria scope = i.getArgument(1);
            return scope.isAllScope() ? new HashMap<>(row) : null;
        });
        AspectJProxyFactory factory = new AspectJProxyFactory(service);
        factory.setProxyTargetClass(true);
        factory.addAspect(new DataScopeAspect(username -> DataScopeCriteria.selfOnly(99L)));
        ReviewService scoped = factory.getProxy();
        assertThrows(BusinessException.class, () -> scoped.act(100L, ReviewAction.SUBMIT, request(2, null)));
        assertEquals("DRAFT", row.get("status"));
        verifyNoInteractions(history);
    }

    private ReviewActionRequest request(Integer version, String reason) {
        ReviewActionRequest request = new ReviewActionRequest();
        request.setVersion(version);
        request.setReason(reason);
        return request;
    }
}
