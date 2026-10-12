package com.scaffold.bookkeeping.controller;

import com.scaffold.bookkeeping.service.RecordService;
import com.scaffold.bookkeeping.service.RecordHistoryService;
import com.scaffold.bookkeeping.service.ReviewService;
import com.scaffold.bookkeeping.mapper.AttachmentMapper;
import com.scaffold.bookkeeping.mapper.RecordMapper;
import com.scaffold.bookkeeping.mapper.ReviewMapper;
import com.scaffold.framework.security.PermissionResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffold.common.domain.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RecordMoneyResponseTest {
    @Test void recordReadReturnsExactDecimalStringsForBrowserInsteadOfLossyNumbers() throws Exception {
        var service = mock(RecordService.class);
        when(service.get(1L)).thenReturn(Map.of("id",1L,"totalAmount",new BigDecimal("9999999999999999.99"),
                "details",List.of(Map.of("amount",new BigDecimal("0.10")))));
        var mvc = MockMvcBuilders.standaloneSetup(new RecordController(service)).build();
        mvc.perform(get("/api/bookkeeping/records/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").isString()).andExpect(jsonPath("$.data.totalAmount").value("9999999999999999.99"))
                .andExpect(jsonPath("$.data.details[0].amount").value("0.10"));
    }

    @Test void recordListPreservesAmountAndPaginationMetadata() throws Exception {
        var service = mock(RecordService.class);
        when(service.list(any(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(PageResult.of(List.of(Map.of("id", 1L, "totalAmount", new BigDecimal("9999999999999999.99"))), 21, 2, 10));
        var mvc = MockMvcBuilders.standaloneSetup(new RecordController(service)).build();
        mvc.perform(get("/api/bookkeeping/records").param("pageNum", "2").param("pageSize", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows[0].totalAmount").isString())
                .andExpect(jsonPath("$.data.rows[0].totalAmount").value("9999999999999999.99"))
                .andExpect(jsonPath("$.data.total").value(21)).andExpect(jsonPath("$.data.pageNum").value(2));
    }

    @Test void pendingListUsesTheSameExactMoneyContract() throws Exception {
        var service = mock(ReviewService.class);
        when(service.pending(any(), isNull(), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(PageResult.of(List.of(Map.of("id", 1L, "totalAmount", new BigDecimal("0.10"))), 1, 1, 10));
        var mvc = MockMvcBuilders.standaloneSetup(new ReviewController(service, mock(RecordHistoryService.class))).build();
        mvc.perform(get("/api/bookkeeping/reviews/pending"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows[0].totalAmount").isString())
                .andExpect(jsonPath("$.data.rows[0].totalAmount").value("0.10"));
    }

    @Test void persistedNumericHistoryKeepsExactMoneyThroughParsingAndHttpSerialization() throws Exception {
        var records = mock(RecordMapper.class);
        var reviews = mock(ReviewMapper.class);
        when(records.selectRecordForHistory(eq(1L), any())).thenReturn(Map.of("id", 1L));
        when(reviews.selectVersions(1L, 0, 10)).thenReturn(List.of(Map.of(
                "id", 5L, "recordId", 1L, "versionNo", 1, "action", "UPDATE", "operatorId", 7L,
                "snapshotJson", "{\"before\":null,\"after\":{\"totalAmount\":9999999999999999.99,\"details\":[{\"amount\":0.10},{\"amount\":100}]}}")));
        when(reviews.countVersions(1L)).thenReturn(1L);
        var history = new RecordHistoryService(records, mock(AttachmentMapper.class), reviews,
                new ObjectMapper(), mock(PermissionResolver.class));
        var mvc = MockMvcBuilders.standaloneSetup(new ReviewController(mock(ReviewService.class), history)).build();
        mvc.perform(get("/api/bookkeeping/records/1/versions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows[0].before").doesNotExist())
                .andExpect(jsonPath("$.data.rows[0].after.totalAmount").isString())
                .andExpect(jsonPath("$.data.rows[0].after.totalAmount").value("9999999999999999.99"))
                .andExpect(jsonPath("$.data.rows[0].after.details[0].amount").value("0.10"))
                .andExpect(jsonPath("$.data.rows[0].after.details[1].amount").value("100.00"));
    }
}
