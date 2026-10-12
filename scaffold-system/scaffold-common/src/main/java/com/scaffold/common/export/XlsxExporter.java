package com.scaffold.common.export;

import com.scaffold.common.exception.BusinessException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** 公共 Excel 导出：文本单元格保留金额精度，同时避免用户内容被当成公式执行。 */
public final class XlsxExporter {
    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private XlsxExporter() {}
    public record Sheet(String name, List<String> headers, List<List<String>> rows) {}

    public static byte[] write(List<Sheet> sheets) {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var headerStyle = workbook.createCellStyle();
            var font = workbook.createFont(); font.setBold(true); headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            for (var source : sheets) {
                if (source.rows().size() > 50000) throw new BusinessException("Excel 明细超过50000行，请缩小日期范围");
                var sheet = workbook.createSheet(source.name());
                writeRow(sheet.createRow(0), source.headers(), headerStyle);
                for (int i = 0; i < source.rows().size(); i++) writeRow(sheet.createRow(i + 1), source.rows().get(i), null);
                sheet.createFreezePane(0, 1);
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, source.rows().size(), 0, source.headers().size() - 1));
                for (int i = 0; i < source.headers().size(); i++) sheet.setColumnWidth(i, 22 * 256);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) { throw new BusinessException("Excel 生成失败，请重试"); }
    }
    private static void writeRow(Row row, List<String> values, CellStyle style) {
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i) == null ? "" : values.get(i);
            if (value.length() > 32767) throw new BusinessException("Excel 单元格内容超长，请缩小导出内容");
            var cell = row.createCell(i, CellType.STRING); cell.setCellValue(value);
            if (style != null) cell.setCellStyle(style);
        }
    }
}
