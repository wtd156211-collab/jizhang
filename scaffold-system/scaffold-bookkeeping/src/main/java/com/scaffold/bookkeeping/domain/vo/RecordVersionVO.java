package com.scaffold.bookkeeping.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/** versionNo 版本号；action 触发动作；before/after 修改前后账目（含明细和授权可见的附件元数据）；
 * reason 原因；operatorId 操作人 ID；operatorName 当前账号名；createTime 快照时间。 */
public record RecordVersionVO(Long id, Long recordId, Integer versionNo, String action,
                              String reason, Long operatorId, String operatorName, String createTime,
                              @JsonSerialize(using = RecordResponse.SnapshotSerializer.class) JsonNode before,
                              @JsonSerialize(using = RecordResponse.SnapshotSerializer.class) JsonNode after) {
}
