package com.scaffold.bookkeeping.service;

import com.scaffold.bookkeeping.domain.dto.RecordDetailWriteRequest;
import com.scaffold.bookkeeping.domain.dto.RecordWriteRequest;
import com.scaffold.bookkeeping.mapper.RecordMapper;
import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.framework.datascope.DataScope;
import com.scaffold.framework.datascope.DataScopeContext;
import com.scaffold.framework.datascope.DataScopeCriteria;
import com.scaffold.system.domain.vo.IdResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 账目草稿服务。入账前允许编辑，入账后的修改由审核模块通过撤回或作废完成。 */
@Service
public class RecordService {

    private static final Set<String> EDITABLE_STATUSES = Set.of("DRAFT", "RETURNED", "WITHDRAWN");
    private static final Set<String> RECORD_STATUSES = Set.of("DRAFT", "PENDING_REVIEW", "RETURNED", "POSTED", "WITHDRAWN", "VOIDED");

    private final RecordMapper mapper;
    private final RecordHistoryService history;

    public RecordService(RecordMapper mapper, RecordHistoryService history) {
        this.mapper = mapper;
        this.history = history;
    }

    @DataScope
    public PageResult<Map<String, Object>> list(PageQuery query, String recordType, String status,
                                                LocalDate occurredFrom, LocalDate occurredTo,
                                                Long categoryId, String keyword) {
        String normalizedType = nullableUpper(recordType);
        if (normalizedType != null && !Set.of("INCOME", "EXPENSE").contains(normalizedType)) {
            throw new BusinessException("账目类型只能为收入或支出");
        }
        String normalizedStatus = nullableUpper(status);
        if (normalizedStatus != null && !RECORD_STATUSES.contains(normalizedStatus)) {
            throw new BusinessException("账目状态不正确");
        }
        if (occurredFrom != null && occurredTo != null && occurredFrom.isAfter(occurredTo)) {
            throw new BusinessException("发生日期范围不正确");
        }
        Page page = pageOf(query);
        DataScopeCriteria scope = DataScopeContext.current();
        String normalizedKeyword = trimToNull(keyword);
        return PageResult.of(mapper.selectRecords(scope, normalizedType, normalizedStatus, occurredFrom, occurredTo,
                        categoryId, normalizedKeyword, page.offset(), page.pageSize()),
                mapper.countRecords(scope, normalizedType, normalizedStatus, occurredFrom, occurredTo, categoryId, normalizedKeyword),
                page.pageNum(), page.pageSize());
    }

    @DataScope
    public Map<String, Object> get(Long id) {
        requireId(id);
        Map<String, Object> row = mapper.selectRecordForWrite(id, DataScopeContext.current());
        if (row == null) {
            throw new BusinessException("账目不存在或无权访问");
        }
        Map<String, Object> result = new LinkedHashMap<>(row);
        result.put("details", mapper.selectDetails(id));
        return result;
    }

    @DataScope
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public IdResponse create(RecordWriteRequest request) {
        Long userId = currentUserId();
        ValidatedWrite validated = validateWrite(request);
        Map<String, Object> row = recordRow(validated, userId);
        mapper.insertRecord(row, currentUsername());
        Long recordId = numberAsLong(row.get("id"));
        for (int i = 0; i < validated.details().size(); i++) {
            mapper.insertDetail(detailRow(recordId, validated.details().get(i), i), currentUsername());
        }
        history.append(recordId, 0, "CREATE", null, userId, null);
        return new IdResponse(recordId);
    }

    @DataScope
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void update(Long id, RecordWriteRequest request) {
        updateDraft(id, request, "UPDATE", null);
    }

    /** OCR 确认复用全部草稿校验与快照，只补充来源留痕；不改变审核状态。 */
    @DataScope
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void confirmOcr(Long id, RecordWriteRequest request, Long taskId) {
        updateDraft(id, request, "OCR_CONFIRM", "OCR task #" + taskId);
    }

    private void updateDraft(Long id, RecordWriteRequest request, String action, String reason) {
        requireId(id);
        Long userId = currentUserId();
        if (request.getVersion() == null || request.getVersion() < 0) {
            throw new BusinessException("缺少账目版本号，请刷新后重试");
        }
        Map<String, Object> existing = mapper.lockRecordForWrite(id, DataScopeContext.current());
        if (existing == null) {
            throw new BusinessException("账目不存在或无权访问");
        }
        String existingStatus = String.valueOf(existing.get("status"));
        if (!EDITABLE_STATUSES.contains(existingStatus)) {
            throw new BusinessException("当前账目状态不允许直接编辑，请撤回重审或作废");
        }
        ValidatedWrite validated = validateWrite(request);
        var before = history.capture(existing);
        Map<String, Object> row = recordRow(validated, userId);
        if (mapper.updateRecord(row, id, request.getVersion(), currentUsername()) == 0) {
            throw new BusinessException("账目已被其他人修改，请刷新后重试");
        }
        mapper.deleteDetails(id);
        for (int i = 0; i < validated.details().size(); i++) {
            mapper.insertDetail(detailRow(id, validated.details().get(i), i), currentUsername());
        }
        history.append(id, request.getVersion() + 1, action, reason, userId, before);
    }

    @DataScope
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long id) {
        requireId(id);
        Long userId = currentUserId();
        Map<String, Object> existing = mapper.lockRecordForWrite(id, DataScopeContext.current());
        if (existing == null) {
            throw new BusinessException("账目不存在或无权访问");
        }
        if (!EDITABLE_STATUSES.contains(String.valueOf(existing.get("status")))) {
            throw new BusinessException("只有草稿、退回或撤回重审账目可以删除");
        }
        var before = history.capture(existing);
        if (mapper.softDeleteRecord(id, currentUsername()) == 0) {
            throw new BusinessException("账目已被其他人修改，请刷新后重试");
        }
        history.append(id, ((Number) existing.get("version")).intValue() + 1, "DELETE", null, userId, before);
    }

    private ValidatedWrite validateWrite(RecordWriteRequest request) {
        if (request == null) {
            throw new BusinessException("账目参数不能为空");
        }
        String recordType = nullableUpper(request.getRecordType());
        if (recordType == null || !Set.of("INCOME", "EXPENSE").contains(recordType)) {
            throw new BusinessException("账目类型只能为收入或支出");
        }
        String summary = required(request.getSummary(), "账目摘要不能为空");
        LocalDate occurredDate = request.getOccurredDate();
        if (occurredDate == null) {
            throw new BusinessException("实际发生日期不能为空");
        }
        BigDecimal totalAmount = money(request.getTotalAmount(), "账目总金额必须大于0");
        List<RecordDetailWriteRequest> details = request.getDetails();
        if (details == null || details.isEmpty()) {
            throw new BusinessException("至少需要一条账目明细");
        }
        if (details.size() > 100) throw new BusinessException("账目明细不能超过100条");
        validateRecordDept(request.getDeptId());
        List<Long> categoryIds = new ArrayList<>();
        BigDecimal detailTotal = BigDecimal.ZERO.setScale(2);
        for (RecordDetailWriteRequest detail : details) {
            if (detail == null || detail.getCategoryId() == null || detail.getCategoryId() < 1) {
                throw new BusinessException("明细分类不能为空");
            }
            BigDecimal amount = money(detail.getAmount(), "明细金额必须大于0");
            if (detail.getHandlerId() == null && detail.getDeptId() == null && detail.getProjectId() == null) {
                throw new BusinessException("每条明细至少需要经办人、部门或项目之一");
            }
            if (detail.getHandlerId() != null && (detail.getHandlerId() < 1 || mapper.countActiveHandler(detail.getHandlerId()) == 0)) {
                throw new BusinessException("明细经办人不存在或已停用");
            }
            if (detail.getDeptId() != null && (detail.getDeptId() < 1 || mapper.countActiveDept(detail.getDeptId()) == 0)) {
                throw new BusinessException("明细部门不存在或已停用");
            }
            if (detail.getProjectId() != null && (detail.getProjectId() < 1 || mapper.countActiveProject(detail.getProjectId()) == 0)) {
                throw new BusinessException("明细项目不存在或已停用");
            }
            categoryIds.add(detail.getCategoryId());
            detailTotal = detailTotal.add(amount);
        }
        if (detailTotal.compareTo(totalAmount) != 0) {
            throw new BusinessException("账目总金额必须等于明细金额合计");
        }
        List<Long> uniqueCategoryIds = categoryIds.stream().distinct().toList();
        if (mapper.countActiveCategories(uniqueCategoryIds, recordType) != uniqueCategoryIds.size()) {
            throw new BusinessException("存在与账目类型不匹配、停用或不存在的分类");
        }
        if (request.getPaymentMethodId() == null || mapper.countActivePaymentMethod(request.getPaymentMethodId()) == 0) {
            throw new BusinessException("支付方式不存在或已停用");
        }
        if (request.getFundAccountId() == null || mapper.countActiveFundAccount(request.getFundAccountId()) == 0) {
            throw new BusinessException("资金账户不存在或已停用");
        }
        return new ValidatedWrite(recordType, summary, occurredDate, request.getOccurredTime(), totalAmount,
                request.getPaymentMethodId(), request.getFundAccountId(), request.getDeptId(), trimToNull(request.getRemark()), details);
    }

    private void validateRecordDept(Long deptId) {
        if (deptId == null) return;
        if (deptId < 1 || mapper.countActiveDept(deptId) == 0) throw new BusinessException("所属部门不存在或已停用");
        DataScopeCriteria scope = DataScopeContext.current();
        if (!scope.isAllScope() && !scope.getDeptIds().contains(deptId)
                && !Objects.equals(deptId, mapper.selectUserDeptIdByUsername(currentUsername()))) {
            throw new BusinessException(403, "无权将账目归属到该部门");
        }
    }

    private Map<String, Object> recordRow(ValidatedWrite value, Long userId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("recordNo", "REC" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase());
        row.put("recordType", value.recordType());
        row.put("summary", value.summary());
        row.put("occurredDate", value.occurredDate());
        row.put("occurredTime", value.occurredTime());
        row.put("totalAmount", value.totalAmount());
        row.put("paymentMethodId", value.paymentMethodId());
        row.put("fundAccountId", value.fundAccountId());
        row.put("createdBy", userId);
        row.put("deptId", value.deptId());
        row.put("remark", value.remark());
        return row;
    }

    private Map<String, Object> detailRow(Long recordId, RecordDetailWriteRequest detail, int index) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("recordId", recordId);
        row.put("categoryId", detail.getCategoryId());
        row.put("amount", detail.getAmount().setScale(2, RoundingMode.UNNECESSARY));
        row.put("handlerId", detail.getHandlerId());
        row.put("deptId", detail.getDeptId());
        row.put("projectId", detail.getProjectId());
        row.put("remark", trimToNull(detail.getRemark()));
        row.put("sort", detail.getSort() == null ? index : detail.getSort());
        return row;
    }

    private BigDecimal money(BigDecimal value, String message) {
        if (value == null || value.signum() <= 0 || value.scale() > 2) {
            throw new BusinessException(message + "且最多保留两位小数");
        }
        if (value.compareTo(new BigDecimal("9999999999999999.99")) > 0) {
            throw new BusinessException("金额不能超过9999999999999999.99");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private Long currentUserId() {
        Long userId = mapper.selectUserIdByUsername(currentUsername());
        if (userId == null || userId < 1) {
            throw new BusinessException(401, "未认证或登录已过期");
        }
        return userId;
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : String.valueOf(authentication.getPrincipal());
    }

    private String required(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String nullableUpper(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private void requireId(Long id) {
        if (id == null || id < 1) {
            throw new BusinessException("账目 ID 不正确");
        }
    }

    private long numberAsLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    private Page pageOf(PageQuery query) {
        PageQuery effective = query == null ? new PageQuery(null, null, null, null) : query;
        int pageNum = effective.normalizedPageNum();
        int pageSize = effective.normalizedPageSize();
        return new Page(pageNum, pageSize, (pageNum - 1) * pageSize);
    }

    private record ValidatedWrite(String recordType, String summary, LocalDate occurredDate, LocalTime occurredTime,
                                  BigDecimal totalAmount, Long paymentMethodId, Long fundAccountId, Long deptId,
                                  String remark, List<RecordDetailWriteRequest> details) {
    }

    private record Page(int pageNum, int pageSize, int offset) {
    }
}
