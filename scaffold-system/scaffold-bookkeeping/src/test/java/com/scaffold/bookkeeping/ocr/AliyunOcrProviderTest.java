package com.scaffold.bookkeeping.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AliyunOcrProviderTest {
    final ObjectMapper json = new ObjectMapper();
    final AliyunOcrTransport transport = mock(AliyunOcrTransport.class);
    final AliyunOcrProvider provider = new AliyunOcrProvider(transport, json);
    @TempDir Path root;
    @Test void normalizesInvoiceWithoutInventingMissingOccurrenceTime() throws Exception {
        var response = json.createObjectNode(); response.put("Code", "200");
        var data = response.putObject("Data"); data.put("Content", "发票");
        data.putArray("SubImages").addObject().putObject("KvInfo").put("Data",
                "{\"totalAmount\":\"￥1,234.50\",\"invoiceDate\":\"2026年10月12日\",\"sellerName\":\"办公用品店\"}");
        when(transport.recognize(any(), anyString(), anyString(), anyString())).thenReturn(response);
        var result = provider.recognize(original(), "image/png", "", "test-id", "test-secret", "Invoice");
        assertEquals("1234.50", result.normalizedResult().get("totalAmount").asText());
        assertEquals("2026-10-12", result.normalizedResult().get("occurredDate").asText());
        assertFalse(result.normalizedResult().has("occurredTime"));
        assertEquals("办公用品店", result.normalizedResult().get("summary").asText());
    }
    @Test void paymentTimestampPreservesExplicitTimeAndInvalidMoneyIsLeftForManualInput() throws Exception {
        var root = json.createObjectNode(); root.put("Code", "200");
        var data = root.putObject("Data"); data.put("Content", "付款");
        data.putArray("SubImages").addObject().putObject("KvInfo").put("Data", "{\"totalAmount\":\"看不清\",\"paymentTime\":\"2026-10-12 09:08:07\",\"description\":\"设备采购\"}");
        when(transport.recognize(any(), anyString(), anyString(), anyString())).thenReturn(root);
        var result = provider.recognize(original(), "image/jpeg", "", "test-id", "test-secret", "PaymentRecord");
        assertEquals("09:08:07", result.normalizedResult().get("occurredTime").asText());
        assertEquals("2026-10-12", result.normalizedResult().get("occurredDate").asText());
        assertFalse(result.normalizedResult().has("totalAmount"));
    }
    @Test void rejectsBusinessFailureMultipleReceiptsAndUntrustedEndpoint() throws Exception {
        when(transport.recognize(any(), anyString(), anyString(), anyString())).thenReturn(json.readTree("{\"Code\":\"InvalidImage\",\"Message\":\"test-secret\"}"));
        assertThrows(Exception.class, () -> provider.recognize(original(), "image/png", "", "id", "secret", "Invoice"));
        when(transport.recognize(any(), anyString(), anyString(), anyString())).thenReturn(json.readTree("{\"Code\":\"200\",\"Data\":{\"SubImages\":[{},{}]}}"));
        assertThrows(Exception.class, () -> provider.recognize(original(), "image/png", "", "id", "secret", "Invoice"));
        assertThrows(Exception.class, () -> provider.recognize(original(), "image/png", "https://evil.example.com", "id", "secret", "Invoice"));
    }
    private Path original() throws Exception { return Files.write(root.resolve("original.png"), new byte[]{1,2,3}); }
}
