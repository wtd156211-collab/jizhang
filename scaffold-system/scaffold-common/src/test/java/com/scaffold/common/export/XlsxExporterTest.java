package com.scaffold.common.export;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class XlsxExporterTest {
    @Test void readerPreservesLargeAmountsAndNeverExecutesUserContentAsFormula() throws Exception {
        byte[] bytes = XlsxExporter.write(List.of(new XlsxExporter.Sheet("账目", List.of("金额", "摘要"),
                List.of(List.of("19999999999999999.98", "=HYPERLINK(\"https://example.com\")")))));
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var row = book.getSheetAt(0).getRow(1);
            assertEquals("19999999999999999.98", row.getCell(0).getStringCellValue());
            assertEquals(CellType.STRING, row.getCell(1).getCellType());
            assertTrue(row.getCell(1).getStringCellValue().startsWith("="));
            assertEquals(1, book.getSheetAt(0).getLastRowNum());
        }
    }
    @Test void emptySheetStillContainsChineseHeaders() throws Exception {
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(XlsxExporter.write(List.of(
                new XlsxExporter.Sheet("每日收支", List.of("发生日期", "收入（元）"), List.of())))))) {
            assertEquals("发生日期", book.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
        }
    }
}
