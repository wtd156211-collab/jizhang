package com.scaffold.bookkeeping.service;

import com.scaffold.bookkeeping.mapper.ReportMapper;
import com.scaffold.bookkeeping.mapper.RecordMapper;
import com.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ReportServiceTest {
    final ReportMapper mapper = mock(ReportMapper.class);
    final RecordMapper records = mock(RecordMapper.class);
    final LocalDate from = LocalDate.of(2026, 10, 1), to = from.plusDays(2);

    @Test void summaryFillsMissingDaysAndReturnsExactTotalsBeyondSingleRecordLimit() {
        when(mapper.daily(any(), eq(from), eq(to), isNull())).thenReturn(List.of(Map.of(
                "date", "2026-10-02", "income", new BigDecimal("19999999999999999.98"),
                "expense", new BigDecimal("0.30"), "count", 3L)));
        var result = new ReportService(mapper, records).summary(from, to, null);
        assertEquals("19999999999999999.98", result.get("income"));
        assertEquals("19999999999999999.68", result.get("net"));
        var daily = (List<?>) result.get("daily");
        assertEquals(3, daily.size());
        assertEquals("0.00", ((Map<?,?>) daily.get(0)).get("income"));
        assertEquals("0.30", ((Map<?,?>) daily.get(1)).get("expense"));
    }
    @Test void invertedOrOversizedPeriodFailsBeforeQuerying() {
        var service = new ReportService(mapper, records);
        assertThrows(BusinessException.class, () -> service.summary(to, from, null));
        assertThrows(BusinessException.class, () -> service.summary(from, from.plusDays(366), null));
        assertThrows(BusinessException.class, () -> service.summary(from, to, "BANK"));
        verifyNoInteractions(mapper, records);
    }
    @Test void emptyPeriodHasZeroTotalsAndNoFalseGrowthRate() {
        var result = new ReportService(mapper, records).summary(from, to, "EXPENSE");
        assertEquals("0.00", result.get("net"));
        assertEquals(0L, result.get("postedCount"));
        assertEquals(0L, result.get("pendingCount"));
    }
    @Test void exportRejectsOversizeInsteadOfSilentlyTruncating() {
        when(records.countRecords(any(), any(), any(), any(), any(), any(), any())).thenReturn(10001L);
        var service = new ReportService(mapper, records);
        assertThrows(BusinessException.class, () -> service.exportRecords(null, null, from, to, null, null));
        verify(records, never()).selectRecords(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }
}
