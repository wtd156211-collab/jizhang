package com.scaffold.bookkeeping.service;

import com.scaffold.bookkeeping.domain.vo.RecordResponse;
import com.scaffold.bookkeeping.mapper.RecordMapper;
import com.scaffold.bookkeeping.mapper.ReportMapper;
import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.common.export.XlsxExporter;
import com.scaffold.framework.datascope.DataScope;
import com.scaffold.framework.datascope.DataScopeContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 数据范围内的财务读模型；响应金额为十进制字符串，导出使用同一数据库读事务。 */
@Service
@Transactional(readOnly = true)
public class ReportService {
    private final ReportMapper mapper;
    private final RecordMapper records;
    private static final Map<String,String> STATES = Map.of("DRAFT","草稿","PENDING_REVIEW","待审核",
            "RETURNED","已退回","POSTED","已入账","WITHDRAWN","撤回重审","VOIDED","已作废");
    public ReportService(ReportMapper mapper, RecordMapper records) { this.mapper = mapper; this.records = records; }

    @DataScope
    public Map<String,Object> summary(LocalDate from, LocalDate to, String recordType) {
        var p = period(from, to); var type = type(recordType); var scope = DataScopeContext.current();
        var days = dailyRows(p, type);
        var result = new LinkedHashMap<String,Object>(totals(days));
        result.put("from", p.from().toString()); result.put("to", p.to().toString()); result.put("daily", days);
        var states = new LinkedHashMap<String,Long>(); STATES.keySet().forEach(s -> states.put(s, 0L));
        for (var row : mapper.states(scope, p.from(), p.to(), type)) states.put(text(row,"status"), number(row,"count"));
        result.put("states", states); result.put("pendingCount", states.get("PENDING_REVIEW"));
        result.put("activeCount", states.entrySet().stream().filter(e -> !e.getKey().equals("VOIDED")).mapToLong(Map.Entry::getValue).sum());
        result.put("categories", categoryRows(p, type));
        result.put("latest", records.selectRecords(scope, type, "POSTED", p.from(), p.to(), null, null, 0, 6)
                .stream().map(RecordResponse::record).toList());
        long length = ChronoUnit.DAYS.between(p.from(), p.to()) + 1;
        var previous = new Period(p.from().minusDays(length), p.from().minusDays(1));
        result.put("previous", totals(dailyRows(previous, type)));
        return result;
    }

    @DataScope
    public List<Map<String,Object>> categories(LocalDate from, LocalDate to, String recordType) {
        return categoryRows(period(from, to), type(recordType));
    }

    @DataScope
    public PageResult<Map<String,Object>> daily(PageQuery query, LocalDate from, LocalDate to, String recordType) {
        var days = new ArrayList<>(dailyRows(period(from, to), type(recordType))); Collections.reverse(days);
        var q = query == null ? new PageQuery(null,null,null,null) : query;
        int size = q.normalizedPageSize(), page = q.normalizedPageNum();
        int start = (int) Math.min(days.size(), (long)(page - 1) * size);
        return PageResult.of(days.subList(start, Math.min(days.size(), start + size)), days.size(), page, size);
    }

    @DataScope
    public byte[] exportReport(LocalDate from, LocalDate to, String recordType) {
        var p = period(from,to); var type = type(recordType);
        var summary = summary(p.from(),p.to(),type);
        var sheets = new ArrayList<XlsxExporter.Sheet>();
        sheets.add(new XlsxExporter.Sheet("统计口径", List.of("项目","内容"), List.of(
                List.of("发生日期", p.from()+" 至 "+p.to()), List.of("状态", "仅统计未删除的已入账账目"),
                List.of("收支类型", type == null ? "全部收支" : typeName(type)), List.of("金额单位", "人民币元；金额单元格为文本以保留精度"),
                List.of("收入", text(summary,"income")), List.of("支出", text(summary,"expense")), List.of("净额",text(summary,"net")))));
        sheets.add(new XlsxExporter.Sheet("每日收支", List.of("发生日期","收入（元）","支出（元）","净额（元）","已入账笔数"),
                dailyRows(p,type).stream().map(r -> cells(r,"date","income","expense","net","count")).toList()));
        sheets.add(new XlsxExporter.Sheet("分类汇总", List.of("分类","收支类型","明细金额（元）","涉及账目笔数"),
                categoryRows(p,type).stream().map(r -> List.of(text(r,"categoryName"),typeName(text(r,"recordType")),text(r,"amount"),text(r,"recordCount"))).toList()));
        sheets.addAll(recordSheets(type,"POSTED",p.from(),p.to(),null,null));
        return XlsxExporter.write(sheets);
    }

    @DataScope
    public byte[] exportRecords(String recordType, String status, LocalDate from, LocalDate to, Long categoryId, String keyword) {
        var type = type(recordType); String state = trim(status);
        if (state != null) { state = state.toUpperCase(Locale.ROOT); if (!STATES.containsKey(state)) throw new BusinessException("账目状态不正确"); }
        if (from != null && to != null && from.isAfter(to)) throw new BusinessException("发生日期范围不正确");
        if (categoryId != null && categoryId <= 0) throw new BusinessException("分类不正确");
        return XlsxExporter.write(recordSheets(type,state,from,to,categoryId,trim(keyword)));
    }

    private List<XlsxExporter.Sheet> recordSheets(String type, String state, LocalDate from, LocalDate to, Long categoryId, String keyword) {
        var scope = DataScopeContext.current();
        if (records.countRecords(scope,type,state,from,to,categoryId,keyword) > 10000) throw new BusinessException("导出超过10000笔账目，请缩小查询范围");
        var rows = records.selectRecords(scope,type,state,from,to,categoryId,keyword,0,10001);
        if (rows.size() > 10000) throw new BusinessException("导出超过10000笔账目，请缩小查询范围");
        var ids = rows.stream().map(r -> ((Number)r.get("id")).longValue()).toList();
        var details = ids.isEmpty() ? List.<Map<String,Object>>of() : mapper.exportDetails(ids);
        if (details.size() > 50000) throw new BusinessException("导出超过50000条明细，请缩小查询范围");
        var labels = new HashMap<Long,Map<String,Object>>();
        if (!ids.isEmpty()) for (var row : mapper.exportLabels(ids)) labels.put(((Number)row.get("id")).longValue(),row);
        var recordRows = rows.stream().map(r -> {
            var label = labels.getOrDefault(((Number)r.get("id")).longValue(),Map.of());
            return List.of(text(r,"recordNo"),typeName(text(r,"recordType")),STATES.getOrDefault(text(r,"status"),text(r,"status")),
                    text(r,"summary"),text(r,"occurredDate"),text(r,"occurredTime"),money(r.get("totalAmount")),
                    text(label,"paymentMethodName"),text(label,"fundAccountName"),text(label,"creatorName"),text(label,"deptName"),text(r,"remark"));
        }).toList();
        return List.of(new XlsxExporter.Sheet("账目",List.of("账目编号","收支类型","状态","摘要","发生日期","发生时间（空表示未记录）",
                "总金额（元）","支付方式","资金账户","录入人","归属部门","备注"),recordRows),
                new XlsxExporter.Sheet("账目明细",List.of("账目编号","分类","明细金额（元）","经办人","部门","项目","备注"),
                        details.stream().map(r -> List.of(text(r,"recordNo"),text(r,"categoryName"),money(r.get("amount")),text(r,"handlerName"),text(r,"deptName"),text(r,"projectName"),text(r,"remark"))).toList()));
    }
    private List<Map<String,Object>> categoryRows(Period p, String type) {
        return mapper.categories(DataScopeContext.current(),p.from(),p.to(),type).stream().map(row -> {
            var copy = new LinkedHashMap<>(row); copy.put("amount",money(row.get("amount"))); return (Map<String,Object>)copy;
        }).toList();
    }
    private List<Map<String,Object>> dailyRows(Period p, String type) {
        var indexed = new HashMap<String,Map<String,Object>>();
        for (var row : mapper.daily(DataScopeContext.current(),p.from(),p.to(),type)) indexed.put(text(row,"date"),row);
        var days = new ArrayList<Map<String,Object>>();
        for (LocalDate date = p.from(); !date.isAfter(p.to()); date = date.plusDays(1)) {
            var row = indexed.getOrDefault(date.toString(),Map.of());
            var income = decimal(row.get("income")); var expense = decimal(row.get("expense"));
            days.add(Map.of("date",date.toString(),"income",money(income),"expense",money(expense),"net",money(income.subtract(expense)),"count",number(row,"count")));
        }
        return days;
    }
    private Map<String,Object> totals(List<Map<String,Object>> days) {
        var income = BigDecimal.ZERO; var expense = BigDecimal.ZERO; long count = 0;
        for (var row : days) { income = income.add(decimal(row.get("income"))); expense = expense.add(decimal(row.get("expense"))); count += number(row,"count"); }
        return Map.of("income",money(income),"expense",money(expense),"net",money(income.subtract(expense)),"postedCount",count);
    }
    private Period period(LocalDate from, LocalDate to) {
        var end = to == null ? LocalDate.now() : to; var start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end) || ChronoUnit.DAYS.between(start,end) >= 366) throw new BusinessException("请选择正确的日期范围，最多366天");
        if (start.getYear() < 1000 || end.getYear() > 9998) throw new BusinessException("日期超出支持范围");
        return new Period(start,end);
    }
    private record Period(LocalDate from, LocalDate to) {}
    private static String type(String value) {
        String result = trim(value); if (result == null) return null; result = result.toUpperCase(Locale.ROOT);
        if (!Set.of("INCOME","EXPENSE").contains(result)) throw new BusinessException("账目类型只能为收入或支出"); return result;
    }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String typeName(String type) { return "INCOME".equals(type) ? "收入" : "支出"; }
    private static BigDecimal decimal(Object value) { return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString()); }
    private static String money(Object value) { return decimal(value).setScale(2).toPlainString(); }
    private static String text(Map<String,Object> row, String key) { return Objects.toString(row.get(key),""); }
    private static long number(Map<String,Object> row, String key) { return row.get(key) == null ? 0L : ((Number)row.get(key)).longValue(); }
    private static List<String> cells(Map<String,Object> row, String... keys) { return Arrays.stream(keys).map(k -> text(row,k)).toList(); }
}
