package com.scaffold.bookkeeping.domain.vo;

/** id 历史 ID；recordId 账目 ID；action 动作；fromStatus/toStatus 前后状态；
 * reason 原因；operatorId 操作人 ID；operatorName 当前账号名；reviewTime 操作时间。 */
public record ReviewHistoryVO(Long id, Long recordId, String action, String fromStatus, String toStatus,
                              String reason, Long operatorId, String operatorName, String reviewTime) {
}
