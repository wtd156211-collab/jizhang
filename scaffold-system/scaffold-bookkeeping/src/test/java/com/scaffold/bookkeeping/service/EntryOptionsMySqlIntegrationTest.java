package com.scaffold.bookkeeping.service;

import com.scaffold.bookkeeping.mapper.BookkeepingConfigMapper;
import com.scaffold.framework.datascope.DataScopeCriteria;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** 实际 SQL 和 DataScope 切面校验，只使用隔离测试容器。 */
@EnabledIfEnvironmentVariable(named="BOOKKEEPING_TEST_JDBC_URL", matches=".+")
class EntryOptionsMySqlIntegrationTest {
    AnnotationConfigApplicationContext context;
    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin", "", List.of()));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }
    @Test void selfScopeGetsOwnUserAndDepartmentWithoutPersonalContactFields() {
        context.getBean(ReviewMySqlIntegrationTest.Scope.class).value.set(DataScopeCriteria.selfOnly(1L));
        var options = context.getBean(BookkeepingConfigService.class).options();
        var users = rows(options, "users");
        assertEquals(1, users.size()); assertEquals(1L, ((Number) users.get(0).get("id")).longValue());
        assertEquals(java.util.Set.of("id","username","nickname"), users.get(0).keySet());
        var depts = rows(options,"depts"); assertEquals(1, depts.size());
        assertEquals(java.util.Set.of("id","name","parentId","status"), depts.get(0).keySet());
        assertTrue(rows(options,"categories").size() >= 5);
        for (String key : List.of("categories", "paymentMethods", "fundAccounts", "projects")) {
            assertTrue(rows(options,key).stream().allMatch(row -> ((Number)row.get("status")).intValue() == 1));
        }
    }
    @Test void unknownSelfScopeDoesNotExposeOrganizationUsersOrDepartments() {
        context.getBean(ReviewMySqlIntegrationTest.Scope.class).value.set(DataScopeCriteria.selfOnly(999999L));
        var options = context.getBean(BookkeepingConfigService.class).options();
        assertTrue(rows(options,"users").isEmpty()); assertTrue(rows(options,"depts").isEmpty());
    }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> rows(Map<String,Object> source, String key) { return (List<Map<String,Object>>)source.get(key); }
    @Configuration @Import(ReviewMySqlIntegrationTest.Config.class)
    static class Config {
        @Bean BookkeepingConfigMapper configMapper(SqlSessionTemplate session) { session.getConfiguration().addMapper(BookkeepingConfigMapper.class); return session.getMapper(BookkeepingConfigMapper.class); }
        @Bean BookkeepingConfigService configService(BookkeepingConfigMapper mapper) { return new BookkeepingConfigService(mapper); }
    }
}
