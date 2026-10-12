package com.scaffold.bookkeeping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffold.bookkeeping.domain.dto.*;
import com.scaffold.bookkeeping.mapper.*;
import com.scaffold.bookkeeping.ocr.OcrProvider;
import com.scaffold.bookkeeping.storage.AttachmentStorage;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.framework.datascope.DataScopeCriteria;
import com.scaffold.framework.security.PermissionResolver;
import com.scaffold.system.domain.dto.ConfigWriteRequest;
import com.scaffold.system.mapper.AuthMapper;
import com.scaffold.system.mapper.SystemManagementMapper;
import com.scaffold.system.service.SecretConfigService;
import com.scaffold.system.service.SystemManagementService;
import com.scaffold.system.service.SystemPermissionResolver;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 无真实云调用：实际 MySQL、事务、数据范围与磁盘原文件；只替换外部 OCR 服务。 */
@EnabledIfEnvironmentVariable(named = "BOOKKEEPING_TEST_JDBC_URL", matches = ".+")
class OcrMySqlIntegrationTest {
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc;
    RecordService records;
    OcrService ocr;
    StubProvider provider;
    @TempDir Path root;
    Long recordId, attachmentId;
    Path original;
    @BeforeEach void setup() throws Exception {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        records = context.getBean(RecordService.class); ocr = context.getBean(OcrService.class);
        provider = context.getBean(StubProvider.class);
        authenticate();
        context.getBean(OcrConfigService.class).save(config("stub", "test-secret-value-1234"));
        recordId = records.create(write("100.00", null)).id();
        original = Files.write(root.resolve("original.png"), new byte[]{(byte)137,80,78,71,13,10,26,10});
        when(context.getBean(AttachmentStorage.class).load(anyString())).thenReturn(original);
        var row = new HashMap<String,Object>();
        row.put("recordId", recordId); row.put("originalName", "原图.png"); row.put("storageKey", "private/original.png");
        row.put("mimeType", "image/png"); row.put("fileSize", 8); row.put("sha256", "test-hash"); row.put("isPrimary", 1); row.put("uploadedBy", 1);
        context.getBean(AttachmentMapper.class).insertAttachment(row, "admin"); attachmentId = ((Number)row.get("id")).longValue();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); if (context != null) context.close(); }

    @Test void successfulOcrKeepsOriginalAndManualConfirmationAddsHistoryWithoutPosting() throws Exception {
        Long task = ocr.recognize(recordId, start()).id();
        assertEquals("SUCCEEDED", ocr.get(task).status());
        assertEquals("100.00", ((BigDecimal)records.get(recordId).get("totalAmount")).toPlainString());
        assertArrayEquals(new byte[]{(byte)137,80,78,71,13,10,26,10}, Files.readAllBytes(original));
        String raw = jdbc.queryForObject("SELECT raw_result FROM acct_ocr_task WHERE id=?", String.class, task);
        assertFalse(raw.contains("test-secret-value-1234"));
        assertFalse(new ObjectMapper().writeValueAsString(ocr.get(task)).contains("rawResult"));
        ocr.confirm(task, write("123.45", 0));
        assertEquals("CONFIRMED", ocr.get(task).status());
        assertEquals("DRAFT", records.get(recordId).get("status"));
        assertEquals(1, records.get(recordId).get("version"));
        assertEquals("123.45", ((BigDecimal)records.get(recordId).get("totalAmount")).toPlainString());
        var version = context.getBean(RecordHistoryService.class).versions(recordId, null).rows().get(0);
        assertEquals("OCR_CONFIRM", version.action()); assertTrue(version.reason().contains(task.toString()));
        assertThrows(BusinessException.class, () -> ocr.confirm(task, write("200.00", 1)));
    }
    @Test void cloudFailureIsSanitizedAndMissingConfigKeepsFilesAndManualEntryAvailable() {
        provider.fail = true;
        Long task = ocr.recognize(recordId, start()).id();
        assertEquals("FAILED", ocr.get(task).status());
        assertFalse(ocr.get(task).errorMessage().contains("test-secret-value-1234"));
        assertTrue(Files.isRegularFile(original));
        assertThrows(BusinessException.class, () -> ocr.confirm(task, write("120.00", 0)));
        records.update(recordId, write("120.00", 0));
        var clear = config("stub", null); clear.setClearApiKey(true); context.getBean(OcrConfigService.class).save(clear);
        var retry = start(); retry.setVersion(1);
        assertEquals("FAILED", ocr.get(ocr.recognize(recordId, retry).id()).status());
        assertEquals(1, records.get(recordId).get("version"));
    }
    @Test void staleConfirmationAndSnapshotFailureRollBackTaskRecordAndDetailsTogether() {
        Long task = ocr.recognize(recordId, start()).id();
        records.update(recordId, write("200.00", 0));
        assertThrows(BusinessException.class, () -> ocr.confirm(task, write("300.00", 0)));
        assertEquals("SUCCEEDED", ocr.get(task).status());
        jdbc.update("INSERT INTO acct_record_version(record_id,version_no,action,snapshot_json,operator_id) VALUES (?,2,'FAULT','{}',1)", recordId);
        assertThrows(RuntimeException.class, () -> ocr.confirm(task, write("300.00", 1)));
        assertEquals("SUCCEEDED", ocr.get(task).status());
        assertEquals("200.00", ((BigDecimal)records.get(recordId).get("totalAmount")).toPlainString());
        assertEquals(1, records.get(recordId).get("version"));
    }
    @Test void anotherRecordAttachmentAndOutOfScopeTaskAreRejected() {
        Long other = records.create(write("100.00", null)).id();
        assertThrows(BusinessException.class, () -> ocr.recognize(other, start()));
        Long task = ocr.recognize(recordId, start()).id();
        context.getBean(ReviewMySqlIntegrationTest.Scope.class).value.set(DataScopeCriteria.selfOnly(999L));
        assertThrows(BusinessException.class, () -> ocr.get(task));
        assertThrows(BusinessException.class, () -> ocr.confirm(task, write("200.00", 0)));
        assertThrows(BusinessException.class, () -> ocr.recognize(recordId, start()));
    }
    @Test void pendingAndPostedRecordsCannotBeRecognizedOrConfirmed() {
        Long task = ocr.recognize(recordId, start()).id();
        var action = new ReviewActionRequest(); action.setVersion(0);
        context.getBean(ReviewService.class).act(recordId, com.scaffold.bookkeeping.domain.ReviewAction.SUBMIT, action);
        assertThrows(BusinessException.class, () -> ocr.recognize(recordId, start()));
        assertThrows(BusinessException.class, () -> ocr.confirm(task, write("200.00", 1)));
        action.setVersion(1); context.getBean(ReviewService.class).act(recordId, com.scaffold.bookkeeping.domain.ReviewAction.APPROVE, action);
        assertThrows(BusinessException.class, () -> ocr.confirm(task, write("200.00", 2)));
    }
    @Test void repeatRecognitionUsesRecentResultAndConcurrentConfirmationsUpdateOnlyOnce() throws Exception {
        Long task = ocr.recognize(recordId, start()).id();
        // 较慢的云响应结束后再收到重复请求，也不能再次计费。
        jdbc.update("UPDATE acct_ocr_task SET create_time=DATE_SUB(NOW(),INTERVAL 30 SECOND) WHERE id=?", task);
        assertEquals(task, ocr.recognize(recordId, start()).id()); assertEquals(1, provider.calls);
        ExecutorService executor = Executors.newFixedThreadPool(2); CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        Callable<Boolean> confirm = () -> { authenticate(); ready.countDown();
            try { assertTrue(go.await(5, TimeUnit.SECONDS)); ocr.confirm(task, write("200.00", 0)); return true; }
            catch (BusinessException ex) { return false; } finally { SecurityContextHolder.clearContext(); } };
        try { var first = executor.submit(confirm); var second = executor.submit(confirm); assertTrue(ready.await(5, TimeUnit.SECONDS)); go.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)); }
        finally { executor.shutdownNow(); }
        assertEquals(1, records.get(recordId).get("version"));
        assertEquals(2, context.getBean(RecordHistoryService.class).versions(recordId, null).total());
    }
    @Test void genericParameterListExportUpdateAndDeleteCannotAccessOcrValues() {
        var mapper = context.getBean(SystemManagementMapper.class);
        var service = new SystemManagementService(mapper, mock(org.springframework.security.crypto.password.PasswordEncoder.class), new SystemPermissionResolver(context.getBean(AuthMapper.class)));
        assertEquals(0, service.configs(null, "bookkeeping.ocr.").total());
        assertFalse(service.exportConfigs(null).contains("bookkeeping.ocr."));
        Long keyId = jdbc.queryForObject("SELECT id FROM sys_config WHERE config_key='bookkeeping.ocr.api_key'", Long.class);
        jdbc.update("UPDATE sys_config SET builtin=0 WHERE id=?", keyId);
        var request = new ConfigWriteRequest(); request.setValue("overwrite");
        assertThrows(BusinessException.class, () -> service.updateConfig(keyId, request));
        assertThrows(BusinessException.class, () -> service.deleteConfig(keyId));
        assertEquals(0, mapper.updateConfig(keyId, Map.of("value", "overwrite"), "admin"));
        assertEquals(0, mapper.deleteConfig(keyId));
    }
    @Test void taskListSurvivesPageReloadButExcludesRemovedAttachmentsAndObeysDataScope() {
        Long task = ocr.recognize(recordId, start()).id();
        var list = ocr.list(recordId, null);
        assertEquals(1, list.total()); assertEquals(task, list.rows().get(0).id());
        context.getBean(ReviewMySqlIntegrationTest.Scope.class).value.set(DataScopeCriteria.selfOnly(999L));
        assertThrows(BusinessException.class, () -> ocr.list(recordId, null));
        context.getBean(ReviewMySqlIntegrationTest.Scope.class).value.set(DataScopeCriteria.all());
        context.getBean(AttachmentMapper.class).softDelete(attachmentId, "admin");
        assertEquals(0, ocr.list(recordId, null).total());
        assertThrows(BusinessException.class, () -> ocr.get(task));
        assertThrows(BusinessException.class, () -> ocr.confirm(task, write("120.00",0)));
    }
    private OcrStartRequest start() { var request = new OcrStartRequest(); request.setAttachmentId(attachmentId); request.setVersion(0); request.setDocumentType("Invoice"); return request; }
    private static void authenticate() { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", "", List.of())); }
    private static OcrConfigRequest config(String provider, String key) { var request = new OcrConfigRequest(); request.setProvider(provider); request.setApiKey(key); return request; }
    private static RecordWriteRequest write(String amount, Integer version) {
        var request = new RecordWriteRequest(); request.setRecordType("EXPENSE"); request.setSummary("OCR 人工确认"); request.setOccurredDate(LocalDate.of(2026,10,12));
        request.setTotalAmount(new BigDecimal(amount)); request.setPaymentMethodId(1L); request.setFundAccountId(1L); request.setDeptId(5L); request.setVersion(version);
        var detail = new RecordDetailWriteRequest(); detail.setCategoryId(3L); detail.setAmount(new BigDecimal(amount)); detail.setDeptId(5L); request.setDetails(List.of(detail)); return request;
    }
    static class StubProvider implements OcrProvider {
        boolean fail; int calls; final ObjectMapper json;
        StubProvider(ObjectMapper json) { this.json = json; }
        public String id() { return "stub"; } public boolean available() { return true; }
        public Result recognize(Path path, String mime, String endpoint, String id, String key, String type) throws Exception {
            calls++; if (fail) throw new IllegalStateException("credential=" + key);
            return new Result(json.readTree("{\"content\":\"原始识别结果\",\"apiKey\":\"" + key + "\"}"),
                    json.readTree("{\"summary\":\"识别建议\",\"totalAmount\":\"123.45\",\"occurredDate\":\"2026-10-12\"}"));
        }
    }
    @Configuration @Import(ReviewMySqlIntegrationTest.Config.class)
    static class Config {
        private <T> T mapper(SqlSessionTemplate session, Class<T> type) { session.getConfiguration().addMapper(type); return session.getMapper(type); }
        @Bean OcrTaskMapper ocrTasks(SqlSessionTemplate session) { return mapper(session, OcrTaskMapper.class); }
        @Bean OcrConfigMapper ocrConfigMapper(SqlSessionTemplate session) { return mapper(session, OcrConfigMapper.class); }
        @Bean AuthMapper authMapper(SqlSessionTemplate session) { return mapper(session, AuthMapper.class); }
        @Bean SystemManagementMapper systemMapper(SqlSessionTemplate session) { return mapper(session, SystemManagementMapper.class); }
        @Bean SecretConfigService secrets() { return new SecretConfigService(Base64.getEncoder().encodeToString(new byte[32])); }
        @Bean StubProvider stubProvider(ObjectMapper json) { return new StubProvider(json); }
        @Bean AttachmentStorage storage() { return mock(AttachmentStorage.class); }
        @Bean OcrConfigService ocrConfig(OcrConfigMapper mapper, SecretConfigService secrets, AuthMapper auth, StubProvider provider) { return new OcrConfigService(mapper, secrets, auth, List.of(provider)); }
        @Bean OcrService ocr(OcrTaskMapper tasks, RecordMapper records, AttachmentMapper attachments, AttachmentStorage storage, OcrConfigService config, RecordService service, ObjectMapper json, PermissionResolver permissions) {
            return new OcrService(tasks, records, attachments, storage, config, service, json, permissions);
        }
    }
}
