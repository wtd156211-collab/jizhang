package com.scaffold.bookkeeping.service;

import com.scaffold.bookkeeping.domain.ReviewAction;
import com.scaffold.bookkeeping.domain.dto.ReviewActionRequest;
import com.scaffold.bookkeeping.mapper.RecordMapper;
import com.scaffold.bookkeeping.mapper.ReviewMapper;
import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.framework.datascope.DataScope;
import com.scaffold.framework.datascope.DataScopeContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDate;
import java.util.Map;

/** 审核动作锁定主表行，条件更新 + 历史 + 前后快照在同一事务提交或回滚。 */
@Service
public class ReviewService {
    private final RecordMapper records;
    private final ReviewMapper reviews;
    private final RecordHistoryService history;
    private final RecordService recordService;

    public ReviewService(RecordMapper records, ReviewMapper reviews, RecordHistoryService history, RecordService recordService) {
        this.records = records;
        this.reviews = reviews;
        this.history = history;
        this.recordService = recordService;
    }

    public PageResult<Map<String, Object>> pending(PageQuery query, String recordType,
                                                  LocalDate occurredFrom, LocalDate occurredTo, Long categoryId, String keyword) {
        return recordService.list(query, recordType, "PENDING_REVIEW", occurredFrom, occurredTo, categoryId, keyword);
    }

    @DataScope
    // 行锁保护账目与附件写入；READ_COMMITTED 保证等待行锁后读取的是最新已提交明细。
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void act(Long id, ReviewAction action, ReviewActionRequest request) {
        if (id == null || id < 1) throw new BusinessException("账目 ID 不正确");
        if (request == null || request.getVersion() == null || request.getVersion() < 0) {
            throw new BusinessException("缺少账目版本号，请刷新后重试");
        }
        if (action == null) throw new BusinessException("审核动作不正确");
        String reason = action.normalizeReason(request.getReason());
        String username = username();
        Long userId = records.selectUserIdByUsername(username);
        if (userId == null || userId < 1) throw new BusinessException(401, "未认证或登录已过期");
        Map<String, Object> row = records.lockRecordForWrite(id, DataScopeContext.current());
        if (row == null) throw new BusinessException("账目不存在或无权访问");
        int version = ((Number) row.get("version")).intValue();
        if (version != request.getVersion()) throw conflict();
        String from = (String) row.get("status");
        String to = action.targetFrom(from);
        var before = history.capture(row);
        if (reviews.updateStatus(id, version, from, to, username) != 1) throw conflict();
        if (reviews.insertReview(id, action.name(), from, to, reason, userId) != 1) {
            throw new BusinessException("审核历史保存失败，请重试");
        }
        history.append(id, version + 1, action.name(), reason, userId, before);
    }

    private BusinessException conflict() {
        return new BusinessException(409, "账目已被其他人修改或审核，请刷新后重试");
    }

    private String username() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }
}
