package com.scaffold.bookkeeping.controller;

import com.scaffold.bookkeeping.service.ReportService;
import com.scaffold.framework.security.PermissionAspect;
import org.junit.jupiter.api.*;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportControllerTest {
    final ReportService service = mock(ReportService.class);
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    ReportController secured(Set<String> permissions) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("admin","",List.of()));
        var resolver = (com.scaffold.framework.security.PermissionResolver)(user,perm) -> permissions.contains(perm);
        var factory = new AspectJProxyFactory(new ReportController(service,resolver)); factory.setProxyTargetClass(true);
        factory.addAspect(new PermissionAspect(resolver)); return factory.getProxy();
    }
    @Test void dashboardPermissionDoesNotGrantReportOrExcelAccess() {
        var api = secured(Set.of("bookkeeping:dashboard:view"));
        assertDoesNotThrow(() -> api.dashboard(null,null));
        assertThrows(AccessDeniedException.class, () -> api.summary(null,null,null));
        assertThrows(AccessDeniedException.class, () -> api.daily(null,null,null,null));
        assertThrows(AccessDeniedException.class, () -> api.category(null,null,null));
        assertThrows(AccessDeniedException.class, () -> api.recordExport(null,null,null,null,null,null));
        assertThrows(AccessDeniedException.class, () -> api.reportExport(null,null,null));
    }
    @Test void reportsAndExportMustBothBeGrantedForReportDownload() {
        var read = secured(Set.of("bookkeeping:report:list"));
        assertDoesNotThrow(() -> read.summary(null,null,null));
        assertThrows(AccessDeniedException.class, () -> read.reportExport(null,null,null));
        var onlyExport = secured(Set.of("bookkeeping:record:export"));
        assertThrows(AccessDeniedException.class, () -> onlyExport.reportExport(null,null,null));
        verify(service,never()).exportReport(any(),any(),any());
        var both = secured(Set.of("bookkeeping:report:list","bookkeeping:record:export"));
        when(service.exportReport(null,null,null)).thenReturn(new byte[]{80,75});
        var response = both.reportExport(null,null,null);
        assertEquals("no-store",response.getHeaders().getCacheControl());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",response.getHeaders().getContentType().toString());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("financial-report.xlsx"));
        assertArrayEquals(new byte[]{80,75},response.getBody());
    }
}
