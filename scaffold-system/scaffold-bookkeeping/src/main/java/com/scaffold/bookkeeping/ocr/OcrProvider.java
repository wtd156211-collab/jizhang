package com.scaffold.bookkeeping.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;

/** 可替换 OCR 服务适配器。只接收已保存的私有原文件；实现必须限制连接/读取超时及响应大小。 */
public interface OcrProvider {
    String id();
    boolean available();
    Result recognize(Path original, String mimeType, String endpoint, String accessKeyId, String apiKey, String documentType) throws Exception;
    /** rawResult 原始响应；normalizedResult 标准化建议字段，仍需人工确认。 */
    record Result(JsonNode rawResult, JsonNode normalizedResult) {}
}
