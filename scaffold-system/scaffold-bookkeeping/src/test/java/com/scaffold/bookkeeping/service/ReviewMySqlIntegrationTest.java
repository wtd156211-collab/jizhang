package com.scaffold.bookkeeping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scaffold.bookkeeping.domain.ReviewAction;
import com.scaffold.bookkeeping.domain.dto.RecordDetailWriteRequest;
import com.scaffold.bookkeeping.domain.dto.RecordWriteRequest;
import com.scaffold.bookkeeping.domain.dto.ReviewActionRequest;
import com.scaffold.bookkeeping.mapper.AttachmentMapper;
import com.scaffold.bookkeeping.mapper.RecordMapper;
import com.scaffold.bookkeeping.mapper.ReviewMapper;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.framework.datascope.DataScopeAspect;
import com.scaffold.framework.datascope.DataScopeCriteria;
import com.scaffold.framework.security.PermissionResolver;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** 仅连接脚本创建的隔离 MySQL 容器；默认不运行，不读取 application-dev.yml。 */
@EnabledIfEnvironmentVariable(named = "BOOKKEEPING_TEST_JDBC_URL", matches = ".+")
class ReviewMySqlIntegrationTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private RecordService records;
    private ReviewService review;
    private RecordHistoryService history;

    @BeforeEach
    void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        records = context.getBean(RecordService.class);
        review = context.getBean(ReviewService.class);
        history = context.getBean(RecordHistoryService.class);
        authenticate();
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        if (context != null) context.close();
    }

    @Test
    void completeWorkflowPreservesEveryVersionAndVoidedAttachments() {
        Long id = records.create(write("100.00", null)).id();
        review.act(id, ReviewAction.SUBMIT, action(0, null));
        review.act(id, ReviewAction.RETURN, action(1, "补充金额说明"));
        records.update(id, write("200.00", 2));
        review.act(id, ReviewAction.SUBMIT, action(3, null));
        review.act(id, ReviewAction.APPROVE, action(4, null));
        assertThrows(BusinessException.class, () -> records.update(id, write("300.00", 5)));
        review.act(id, ReviewAction.WITHDRAW, action(5, "修订金额"));
        records.update(id, write("300.00", 6));
        jdbc.update("INSERT INTO acct_record_attachment (record_id,original_name,storage_key,mime_type,file_size,uploaded_by) VALUES (?,?,?,?,?,?)",
                id, "票据.png", "retained/original.png", "image/png", 8, 1);
        review.act(id, ReviewAction.SUBMIT, action(7, null));
        review.act(id, ReviewAction.APPROVE, action(8, null));
        assertEquals("POSTED", records.get(id).get("status"));
        review.act(id, ReviewAction.VOID, action(9, "重复入账"));

        assertEquals("VOIDED", records.get(id).get("status"));
        assertEquals(10, records.get(id).get("version"));
        assertEquals(8, count("acct_record_review", id));
        assertEquals(11, count("acct_record_version", id));
        assertEquals(1, count("acct_record_detail", id));
        assertEquals(1, count("acct_record_attachment", id));
        var versions = history.versions(id, new com.scaffold.common.domain.PageQuery(1, 100, null, null));
        assertEquals(0, new BigDecimal("300.00").compareTo(versions.rows().get(0).before().get("totalAmount").decimalValue()));
        assertEquals("VOIDED", versions.rows().get(0).after().get("status").asText());
        assertFalse(versions.rows().get(0).after().at("/attachments/0").has("storageKey"));
        assertEquals("VOID", history.reviews(id, null).rows().get(0).action());
        assertEquals("admin", history.reviews(id, null).rows().get(0).operatorName());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM acct_record WHERE id=? AND status='POSTED' AND deleted=0", Long.class, id));
        assertThrows(BusinessException.class, () -> review.act(id, ReviewAction.SUBMIT, action(10, null)));
    }

    @Test
    void deletedDraftKeepsScopedHistoryAndDetails() {
        Long id = records.create(write("100.00", null)).id();
        records.delete(id);
        assertThrows(BusinessException.class, () -> records.get(id));
        assertEquals(1, count("acct_record_detail", id));
        var versions = history.versions(id, null);
        assertEquals(2, versions.total());
        assertEquals("DELETE", versions.rows().get(0).action());
        assertEquals(1, versions.rows().get(0).after().get("deleted").asInt());
        context.getBean(Scope.class).value.set(DataScopeCriteria.selfOnly(999L));
        assertThrows(BusinessException.class, () -> history.versions(id, null));
        assertThrows(BusinessException.class, () -> history.reviews(id, null));
    }

    @Test
    void duplicateVersionFailureRollsBackStatusAndReviewHistory() {
        Long id = records.create(write("100.00", null)).id();
        // 强制历史唯一键冲突，验证实际 MySQL 事务而非只检查 @Transactional 注解。
        jdbc.update("INSERT INTO acct_record_version (record_id,version_no,action,snapshot_json,operator_id) VALUES (?,1,'FAULT','{}',1)", id);
        assertThrows(RuntimeException.class, () -> review.act(id, ReviewAction.SUBMIT, action(0, null)));
        assertEquals("DRAFT", records.get(id).get("status"));
        assertEquals(0, records.get(id).get("version"));
        assertEquals(0, count("acct_record_review", id));
        assertEquals(2, count("acct_record_version", id));
    }

    @Test
    void snapshotFailureRollsBackDraftReplacementAndDetails() {
        Long id = records.create(write("100.00", null)).id();
        jdbc.update("INSERT INTO acct_record_version (record_id,version_no,action,snapshot_json,operator_id) VALUES (?,1,'FAULT','{}',1)", id);
        assertThrows(RuntimeException.class, () -> records.update(id, write("200.00", 0)));
        assertEquals(0, records.get(id).get("version"));
        assertEquals(0, new BigDecimal("100.00").compareTo((BigDecimal) records.get(id).get("totalAmount")));
        assertEquals(0, new BigDecimal("100.00").compareTo(jdbc.queryForObject("SELECT amount FROM acct_record_detail WHERE record_id=?", BigDecimal.class, id)));
    }

    @Test
    void simultaneousApprovalsProduceExactlyOnePostedVersion() throws Exception {
        Long id = records.create(write("100.00", null)).id();
        review.act(id, ReviewAction.SUBMIT, action(0, null));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> approve = () -> {
            authenticate();
            ready.countDown();
            try {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                review.act(id, ReviewAction.APPROVE, action(1, null));
                return true;
            } catch (BusinessException conflict) {
                assertEquals(409, conflict.code());
                return false;
            } finally { SecurityContextHolder.clearContext(); }
        };
        try {
            Future<Boolean> first = executor.submit(approve);
            Future<Boolean> second = executor.submit(approve);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
        assertEquals("POSTED", records.get(id).get("status"));
        assertEquals(2, records.get(id).get("version"));
        assertEquals(2, count("acct_record_review", id));
        assertEquals(3, count("acct_record_version", id));
    }

    @Test
    void pendingListAndHistoryRespectRealSqlDepartmentAndOwnerScope() {
        Long id = records.create(write("100.00", null)).id();
        review.act(id, ReviewAction.SUBMIT, action(0, null));
        String recordNo = (String) records.get(id).get("recordNo");
        assertEquals(1, review.pending(null, "EXPENSE", null, null, null, recordNo).total());
        context.getBean(Scope.class).value.set(DataScopeCriteria.selfOnly(999L));
        assertEquals(0, review.pending(null, null, null, null, null, recordNo).total());
        assertThrows(BusinessException.class, () -> review.act(id, ReviewAction.APPROVE, action(1, null)));
        assertThrows(BusinessException.class, () -> history.versions(id, null));
        context.getBean(Scope.class).value.set(DataScopeCriteria.limited(999L, List.of(5L)));
        assertEquals(1, review.pending(null, null, null, null, null, recordNo).total());
        assertEquals(1, history.reviews(id, null).total());
    }

    @Test
    void waitingForEditCommitCapturesTheLatestDetailsInsteadOfAnEarlierReadView() throws Exception {
        Long id = records.create(write("100.00", null)).id();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch lookup = new CountDownLatch(1);
        AtomicReference<Future<?>> outcome = new AtomicReference<>();
        TransactionTemplate transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        try {
            transaction.executeWithoutResult(status -> {
                jdbc.update("UPDATE acct_record SET version=1,total_amount=200 WHERE id=?", id);
                jdbc.update("UPDATE acct_record_detail SET amount=200 WHERE record_id=?", id);
                // 审核的用户查找先建立旧读视图；随后等待本事务释放主表锁。
                RecordMapper delegate = context.getBean(RecordMapper.class);
                RecordMapper observed = (RecordMapper) java.lang.reflect.Proxy.newProxyInstance(
                        RecordMapper.class.getClassLoader(), new Class<?>[]{RecordMapper.class}, (proxy, method, args) -> {
                            try {
                                Object result = method.invoke(delegate, args);
                                if (method.getName().equals("selectUserIdByUsername")) lookup.countDown();
                                return result;
                            } catch (java.lang.reflect.InvocationTargetException ex) { throw ex.getCause(); }
                        });
                ReviewService target = new ReviewService(observed, context.getBean(ReviewMapper.class), history, records);
                org.springframework.aop.framework.ProxyFactory factory = new org.springframework.aop.framework.ProxyFactory(target);
                factory.setProxyTargetClass(true);
                var attributes = new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource();
                var interceptor = new org.springframework.transaction.interceptor.TransactionInterceptor();
                interceptor.setTransactionManager(context.getBean(PlatformTransactionManager.class));
                interceptor.setTransactionAttributeSource(attributes);
                factory.addAdvice(interceptor);
                ReviewService proxied = (ReviewService) factory.getProxy();
                outcome.set(executor.submit(() -> {
                    authenticate();
                    try { proxied.act(id, ReviewAction.SUBMIT, action(1, null)); }
                    finally { SecurityContextHolder.clearContext(); }
                }));
                try { assertTrue(lookup.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new RuntimeException(ex); }
            });
            executor.shutdown();
            outcome.get().get(10, TimeUnit.SECONDS);
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            var snapshot = history.versions(id, null).rows().get(0).before();
            assertEquals(0, new BigDecimal("200.00").compareTo(snapshot.get("totalAmount").decimalValue()));
            assertEquals(0, new BigDecimal("200.00").compareTo(snapshot.at("/details/0/amount").decimalValue()));
        } finally { executor.shutdownNow(); }
    }

    private long count(String table, Long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE record_id=?", Long.class, id);
    }

    private static void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", "", List.of()));
    }

    private RecordWriteRequest write(String amount, Integer version) {
        RecordWriteRequest request = new RecordWriteRequest();
        request.setRecordType("EXPENSE");
        request.setSummary("审核闭环测试");
        request.setOccurredDate(LocalDate.of(2026, 10, 12));
        request.setTotalAmount(new BigDecimal(amount));
        request.setPaymentMethodId(1L);
        request.setFundAccountId(1L);
        request.setDeptId(5L);
        request.setVersion(version);
        RecordDetailWriteRequest detail = new RecordDetailWriteRequest();
        detail.setCategoryId(3L);
        detail.setAmount(new BigDecimal(amount));
        detail.setDeptId(5L);
        request.setDetails(List.of(detail));
        return request;
    }

    private ReviewActionRequest action(int version, String reason) {
        var request = new ReviewActionRequest();
        request.setVersion(version);
        request.setReason(reason);
        return request;
    }

    static class Scope { final AtomicReference<DataScopeCriteria> value = new AtomicReference<>(DataScopeCriteria.all()); }

    @Configuration
    @EnableTransactionManagement
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    static class Config {
        @Bean DataSource dataSource() {
            String url = System.getenv("BOOKKEEPING_TEST_JDBC_URL");
            if (url == null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/bookkeeping_review_test\\?.*")) {
                throw new IllegalArgumentException("仅允许连接隔离测试容器的 bookkeeping_review_test 库");
            }
            return new DriverManagerDataSource(url, "root", "");
        }
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
        @Bean SqlSessionTemplate sqlSessionTemplate(DataSource dataSource) throws Exception {
            var factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            var config = new org.apache.ibatis.session.Configuration();
            config.setMapUnderscoreToCamelCase(true);
            config.addMapper(RecordMapper.class);
            config.addMapper(ReviewMapper.class);
            config.addMapper(AttachmentMapper.class);
            factory.setConfiguration(config);
            return new SqlSessionTemplate(factory.getObject());
        }
        @Bean RecordMapper recordMapper(SqlSessionTemplate session) { return session.getMapper(RecordMapper.class); }
        @Bean ReviewMapper reviewMapper(SqlSessionTemplate session) { return session.getMapper(ReviewMapper.class); }
        @Bean AttachmentMapper attachmentMapper(SqlSessionTemplate session) { return session.getMapper(AttachmentMapper.class); }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean PermissionResolver permissionResolver() { return (username, permission) -> true; }
        @Bean Scope scope() { return new Scope(); }
        @Bean DataScopeAspect scopeAspect(Scope scope) { return new DataScopeAspect(username -> scope.value.get()); }
        @Bean RecordHistoryService history(RecordMapper records, AttachmentMapper attachments, ReviewMapper reviews, ObjectMapper json, PermissionResolver permissions) {
            return new RecordHistoryService(records, attachments, reviews, json, permissions);
        }
        @Bean RecordService records(RecordMapper records, RecordHistoryService history) { return new RecordService(records, history); }
        @Bean ReviewService review(RecordMapper records, ReviewMapper reviews, RecordHistoryService history, RecordService service) {
            return new ReviewService(records, reviews, history, service);
        }
    }
}
