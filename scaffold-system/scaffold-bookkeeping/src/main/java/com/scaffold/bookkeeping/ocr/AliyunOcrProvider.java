package com.scaffold.bookkeeping.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import java.util.regex.Pattern;

/** RecognizeAllText 的小范围适配：单张票据/支付截图/通用文字，字段只作为建议。 */
@Component
public class AliyunOcrProvider implements OcrProvider {
    public static final String ENDPOINT = "https://ocr-api.cn-hangzhou.aliyuncs.com";
    public static final Set<String> TYPES = Set.of("Invoice", "ShoppingReceipt", "PaymentRecord", "Advanced");
    private final AliyunOcrTransport transport;
    private final ObjectMapper json;
    public AliyunOcrProvider(AliyunOcrTransport transport, ObjectMapper json) { this.transport = transport; this.json = json; }
    public String id() { return "aliyun"; }
    public boolean available() { return true; }

    public Result recognize(Path original, String mimeType, String endpoint, String accessKeyId, String apiKey, String documentType) throws Exception {
        if ((endpoint != null && !endpoint.isBlank() && !ENDPOINT.equals(endpoint)) || !TYPES.contains(documentType)
                || !Set.of("image/png", "image/jpeg", "application/pdf").contains(mimeType) || Files.size(original) > 10485760) {
            throw new IllegalArgumentException("Invalid OCR request");
        }
        if ("application/pdf".equals(mimeType) && !"Invoice".equals(documentType)) throw new IllegalArgumentException("PDF requires Invoice mode");
        JsonNode raw = transport.recognize(original, accessKeyId, apiKey, documentType);
        if (raw == null || !"200".equals(raw.path("Code").asText()) || !raw.path("Data").isObject()) {
            throw new IllegalStateException("OCR request failed");
        }
        if (json.writeValueAsBytes(raw).length > 1048576) throw new IllegalStateException("OCR response too large");
        var normalized = json.createObjectNode();
        String text = raw.path("Data").path("Content").asText("");
        normalized.put("text", text.substring(0, Math.min(text.length(), 10000)));
        JsonNode images = raw.path("Data").path("SubImages");
        if (images.isArray() && images.size() > 1) throw new IllegalStateException("Multiple receipts require manual input");
        JsonNode kv = images.path(0).path("KvInfo").path("Data");
        if (kv.isTextual() && !kv.asText().isBlank()) kv = json.readTree(kv.asText());
        if (kv.isObject()) {
            money(normalized, kv.path("totalAmount").asText(""));
            switch (documentType) {
                case "Invoice" -> { date(normalized, kv.path("invoiceDate").asText("")); summary(normalized, kv.path("sellerName").asText("")); }
                case "ShoppingReceipt" -> { date(normalized, kv.path("receiptDate").asText("")); time(normalized, kv.path("receiptTime").asText("")); summary(normalized, kv.path("shopName").asText("")); }
                case "PaymentRecord" -> {
                    String timestamp = kv.path("paymentTime").asText("");
                    var match = Pattern.compile("(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2})[ T](\\d{1,2}:\\d{2}(?::\\d{2})?)").matcher(timestamp);
                    if (match.matches()) { date(normalized, match.group(1)); time(normalized, match.group(2)); }
                    else date(normalized, timestamp);
                    summary(normalized, kv.path("description").asText(""));
                }
                default -> { /* 通用文字不猜测金额/日期，保留文本供人工填写。 */ }
            }
        }
        return new Result(raw, normalized);
    }
    private void summary(ObjectNode target, String value) { if (!value.isBlank()) target.put("summary", value.substring(0, Math.min(500, value.length()))); }
    private void money(ObjectNode target, String value) {
        try {
            BigDecimal amount = new BigDecimal(value.replace("￥", "").replace("¥", "").replace(",", "").trim()).setScale(2, RoundingMode.UNNECESSARY);
            if (amount.signum() > 0 && amount.precision() <= 18) target.put("totalAmount", amount.toPlainString());
        } catch (IllegalArgumentException | ArithmeticException ignored) { }
    }
    private void date(ObjectNode target, String value) {
        var match = Pattern.compile("(\\d{4})[-/年](\\d{1,2})[-/月](\\d{1,2})日?").matcher(value.trim());
        if (match.matches()) {
            try { target.put("occurredDate", LocalDate.of(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)), Integer.parseInt(match.group(3))).toString()); }
            catch (RuntimeException ignored) { }
        }
    }
    private void time(ObjectNode target, String value) {
        var match = Pattern.compile("(\\d{1,2}):(\\d{2})(?::(\\d{2}))?").matcher(value.trim());
        if (match.matches()) {
            try { target.put("occurredTime", LocalTime.of(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)), match.group(3) == null ? 0 : Integer.parseInt(match.group(3))).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))); }
            catch (RuntimeException ignored) { }
        }
    }
}
