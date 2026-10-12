package com.scaffold.bookkeeping.domain.vo;

import com.fasterxml.jackson.databind.JsonNode;

/** normalizedResult 识别建议，可人工编辑；raw_result 不通过接口返回；errorMessage 为固定脱敏提示。 */
public record OcrTaskVO(Long id, Long recordId, Long attachmentId, String provider, String status,
                        JsonNode normalizedResult, String errorMessage, Long requestedBy,
                        String startedAt, String finishedAt, String createTime) {}
