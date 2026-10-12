package com.scaffold.bookkeeping.ocr;

import org.springframework.stereotype.Component;
import java.nio.file.Path;

/** 未选择实际服务商前禁用识别，不产生伪造成功结果，也不发送原图。 */
@Component
public class DisabledOcrProvider implements OcrProvider {
    public String id() { return "disabled"; }
    public boolean available() { return false; }
    public Result recognize(Path original, String mimeType, String endpoint, String accessKeyId, String apiKey, String documentType) {
        throw new IllegalStateException("未接入 OCR 服务商");
    }
}
