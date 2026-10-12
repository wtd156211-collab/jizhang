package com.scaffold.bookkeeping.domain.vo;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scaffold.common.domain.PageResult;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 仅在记账响应边界把 totalAmount（总金额）、amount（明细金额）转为精确十进制字符串。 */
public final class RecordResponse {
    private RecordResponse() { }

    public static Map<String, Object> record(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        if (source.get("totalAmount") instanceof BigDecimal amount) result.put("totalAmount", decimal(amount));
        if (source.get("details") instanceof List<?> details) {
            result.put("details", details.stream().map(row -> {
                if (!(row instanceof Map<?, ?> detail)) return row;
                Map<Object, Object> copy = new LinkedHashMap<>(detail);
                if (detail.get("amount") instanceof BigDecimal amount) copy.put("amount", decimal(amount));
                return copy;
            }).toList());
        }
        return result;
    }

    public static PageResult<Map<String, Object>> page(PageResult<Map<String, Object>> source) {
        return PageResult.of(source.rows().stream().map(RecordResponse::record).toList(),
                source.total(), source.pageNum(), source.pageSize());
    }

    private static String decimal(BigDecimal amount) {
        return amount.setScale(2).toPlainString();
    }

    /** 历史保持原始数值快照；输出前复制并转金额，兼容已保存的数字和字符串。 */
    public static final class SnapshotSerializer extends JsonSerializer<JsonNode> {
        @Override
        public void serialize(JsonNode value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            JsonNode copy = value.deepCopy();
            if (copy instanceof ObjectNode record) {
                writeMoney(record, "totalAmount");
                if (record.path("details").isArray()) {
                    for (JsonNode detail : record.path("details")) {
                        if (detail instanceof ObjectNode row) writeMoney(row, "amount");
                    }
                }
            }
            generator.writeTree(copy);
        }

        private void writeMoney(ObjectNode row, String field) {
            JsonNode value = row.get(field);
            if (value != null && value.isNumber()) row.put(field, decimal(value.decimalValue()));
        }
    }
}
