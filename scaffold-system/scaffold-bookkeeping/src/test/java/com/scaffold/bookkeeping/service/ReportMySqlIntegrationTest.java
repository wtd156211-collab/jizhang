package com.scaffold.bookkeeping.service;

import com.scaffold.bookkeeping.mapper.ReportMapper;
import com.scaffold.bookkeeping.mapper.RecordMapper;
import com.scaffold.framework.datascope.DataScopeCriteria;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.*;
import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BOOKKEEPING_TEST_JDBC_URL",matches=".+")
class ReportMySqlIntegrationTest {
    AnnotationConfigApplicationContext context; JdbcTemplate jdbc; ReportService reports;
    final LocalDate from = LocalDate.of(2040,2,1), to = LocalDate.of(2040,2,3);
    final List<Long> ids = new ArrayList<>();
    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class)); reports = context.getBean(ReportService.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin","",List.of()));
    }
    @AfterEach void cleanup() {
        for (Long id : ids) { jdbc.update("DELETE FROM acct_record_detail WHERE record_id=?",id); jdbc.update("DELETE FROM acct_record WHERE id=?",id); }
        SecurityContextHolder.clearContext(); context.close();
    }
    long row(String date,String type,String state,String amount,int owner) {
        String no = "FIN"+UUID.randomUUID().toString().replace("-","");
        jdbc.update("INSERT INTO acct_record(record_no,record_type,status,summary,occurred_date,total_amount,payment_method_id,fund_account_id,created_by,dept_id) VALUES(?,?,?,?,?,?,1,1,?,5)",no,type,state,"财务测试 =SUM(A1)",date,amount,owner);
        Long id = jdbc.queryForObject("SELECT id FROM acct_record WHERE record_no=?",Long.class,no); ids.add(id); return id;
    }
    void detail(long id,int category,String amount) { jdbc.update("INSERT INTO acct_record_detail(record_id,category_id,amount,dept_id) VALUES(?,?,?,5)",id,category,amount); }
    @Test void boundariesVoidedDeletedAndMultipleDetailsDoNotDistortTotals() {
        long expense = row("2040-02-01","EXPENSE","POSTED","0.30",1); detail(expense,3,"0.10"); detail(expense,3,"0.20");
        row("2040-02-03","INCOME","POSTED","100.00",1);
        row("2040-01-31","INCOME","POSTED","999.00",1); row("2040-02-04","INCOME","POSTED","999.00",1);
        row("2040-02-02","EXPENSE","VOIDED","1000.00",1); row("2040-02-02","EXPENSE","PENDING_REVIEW","2000.00",1);
        long deleted = row("2040-02-02","INCOME","POSTED","3000.00",1); jdbc.update("UPDATE acct_record SET deleted=1 WHERE id=?",deleted);
        var s = reports.summary(from,to,null);
        assertEquals("100.00",s.get("income")); assertEquals("0.30",s.get("expense")); assertEquals("99.70",s.get("net"));
        assertEquals(2L,s.get("postedCount")); assertEquals(1L,s.get("pendingCount")); assertEquals(3L,s.get("activeCount"));
        assertEquals("0.30",reports.categories(from,to,null).get(0).get("amount"));
        assertEquals(1L,((Number)reports.categories(from,to,null).get(0).get("recordCount")).longValue());
        assertEquals("0.00",((Map<?,?>)((List<?>)s.get("daily")).get(1)).get("income"));
    }
    @Test void ownAndDepartmentScopeApplyToSummaryCategoryAndExport() throws Exception {
        long id = row("2040-02-02","EXPENSE","POSTED","123.45",1); detail(id,3,"123.45");
        var scope = context.getBean(ReviewMySqlIntegrationTest.Scope.class);
        scope.value.set(DataScopeCriteria.selfOnly(999L));
        assertEquals("0.00",reports.summary(from,to,null).get("expense")); assertTrue(reports.categories(from,to,null).isEmpty());
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(reports.exportRecords(null,null,from,to,null,null)))) { assertEquals(0,book.getSheet("账目").getLastRowNum()); }
        scope.value.set(DataScopeCriteria.limited(999L,List.of(5L)));
        assertEquals("123.45",reports.summary(from,to,null).get("expense"));
        assertEquals(1,reports.categories(from,to,null).size());
    }
    @Test void filteredReportWorkbookMatchesScreenAndHasExactMoneyAndUnknownTime() throws Exception {
        long id = row("2040-02-02","EXPENSE","POSTED","9999999999999999.99",1); detail(id,3,"9999999999999999.99");
        row("2040-02-02","INCOME","POSTED","100.00",1); row("2040-02-02","EXPENSE","DRAFT","222.00",1);
        var s = reports.summary(from,to,"EXPENSE");
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(reports.exportReport(from,to,"EXPENSE")))) {
            assertEquals(s.get("expense"),book.getSheet("统计口径").getRow(6).getCell(1).getStringCellValue());
            assertEquals(1,book.getSheet("账目").getLastRowNum()); assertEquals(1,book.getSheet("账目明细").getLastRowNum());
            assertEquals("9999999999999999.99",book.getSheet("账目").getRow(1).getCell(6).getStringCellValue());
            assertEquals("",book.getSheet("账目").getRow(1).getCell(5).getStringCellValue());
            assertEquals("已入账",book.getSheet("账目").getRow(1).getCell(2).getStringCellValue());
        }
    }
    @Test void recordExportUsesKeywordCategoryStatusAndWholeRecordWithAllDetails() throws Exception {
        long id = row("2040-02-02","EXPENSE","DRAFT","10.00",1); detail(id,3,"4.00"); detail(id,4,"6.00");
        row("2040-02-02","EXPENSE","VOIDED","20.00",1);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(reports.exportRecords("EXPENSE","DRAFT",from,to,3L,"=SUM")))) {
            assertEquals(1,book.getSheet("账目").getLastRowNum()); assertEquals(2,book.getSheet("账目明细").getLastRowNum());
            assertEquals("10.00",book.getSheet("账目").getRow(1).getCell(6).getStringCellValue());
            assertEquals("银行转账",book.getSheet("账目").getRow(1).getCell(7).getStringCellValue());
        }
    }
    @Configuration @Import(ReviewMySqlIntegrationTest.Config.class)
    static class Config {
        @Bean ReportMapper reportMapper(SqlSessionTemplate session) { session.getConfiguration().addMapper(ReportMapper.class); return session.getMapper(ReportMapper.class); }
        @Bean ReportService reports(ReportMapper mapper, RecordMapper records) { return new ReportService(mapper,records); }
    }
}
