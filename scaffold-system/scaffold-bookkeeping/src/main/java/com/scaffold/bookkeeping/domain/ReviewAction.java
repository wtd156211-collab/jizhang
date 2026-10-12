package com.scaffold.bookkeeping.domain;

import com.scaffold.common.exception.BusinessException;
import java.util.Set;

/** 单级审核状态策略：已作废没有后续流转，已入账须先撤回再修改。 */
public enum ReviewAction {
    SUBMIT(Set.of("DRAFT", "RETURNED", "WITHDRAWN"), "PENDING_REVIEW", false),
    APPROVE(Set.of("PENDING_REVIEW"), "POSTED", false),
    RETURN(Set.of("PENDING_REVIEW"), "RETURNED", true),
    WITHDRAW(Set.of("POSTED"), "WITHDRAWN", true),
    VOID(Set.of("POSTED"), "VOIDED", true);

    private final Set<String> sources;
    private final String target;
    private final boolean reasonRequired;

    ReviewAction(Set<String> sources, String target, boolean reasonRequired) {
        this.sources = sources;
        this.target = target;
        this.reasonRequired = reasonRequired;
    }

    public String targetFrom(String status) {
        if (!sources.contains(status)) throw new BusinessException("当前账目状态不允许执行该操作");
        return target;
    }

    public String normalizeReason(String reason) {
        String normalized = reason == null || reason.isBlank() ? null : reason.strip();
        if (reasonRequired && normalized == null) throw new BusinessException("退回、撤回和作废必须填写原因");
        if (normalized != null && normalized.length() > 1000) throw new BusinessException("原因不能超过1000个字符");
        return normalized;
    }
}
