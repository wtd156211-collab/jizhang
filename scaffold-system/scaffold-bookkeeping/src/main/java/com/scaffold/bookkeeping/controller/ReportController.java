package com.scaffold.bookkeeping.controller;

import com.scaffold.bookkeeping.service.ReportService;
import com.scaffold.common.domain.*;
import com.scaffold.common.export.XlsxExporter;
import com.scaffold.framework.audit.OperLog;
import com.scaffold.framework.security.RequiresPermission;
import com.scaffold.framework.security.PermissionResolver;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

/** 工作台、报表及 Excel 导出；日期均表示实际发生日期，统计金额仅含已入账记录。 */
@RestController
@RequestMapping("/api/bookkeeping")
public class ReportController {
    private final ReportService service;
    private final PermissionResolver permissions;
    public ReportController(ReportService service, PermissionResolver permissions) { this.service = service; this.permissions = permissions; }
    @GetMapping("/dashboard/summary") @RequiresPermission("bookkeeping:dashboard:view")
    public R<Map<String,Object>> dashboard(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate occurredFrom,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate occurredTo) {
        return R.success(service.summary(occurredFrom,occurredTo,null));
    }
    @GetMapping("/reports/summary") @RequiresPermission("bookkeeping:report:list")
    public R<Map<String,Object>> summary(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate occurredFrom,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate occurredTo, @RequestParam(required=false) String recordType) {
        return R.success(service.summary(occurredFrom,occurredTo,recordType));
    }
    @GetMapping("/reports/daily") @RequiresPermission("bookkeeping:report:list")
    public R<PageResult<Map<String,Object>>> daily(PageQuery query,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate occurredFrom,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate occurredTo, @RequestParam(required=false) String recordType) {
        return R.success(service.daily(query,occurredFrom,occurredTo,recordType));
    }
    @GetMapping("/reports/category") @RequiresPermission("bookkeeping:report:list")
    public R<List<Map<String,Object>>> category(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate occurredFrom,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate occurredTo, @RequestParam(required=false) String recordType) {
        return R.success(service.categories(occurredFrom,occurredTo,recordType));
    }
    @GetMapping("/reports/export") @RequiresPermission("bookkeeping:report:list")
    @OperLog(module="财务报表",action="导出Excel")
    public ResponseEntity<byte[]> reportExport(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate occurredFrom,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate occurredTo, @RequestParam(required=false) String recordType) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !permissions.hasPermission(String.valueOf(auth.getPrincipal()),"bookkeeping:record:export")) throw new AccessDeniedException("没有导出权限");
        return file(service.exportReport(occurredFrom,occurredTo,recordType),"financial-report.xlsx");
    }
    @GetMapping("/records/export") @RequiresPermission("bookkeeping:record:export")
    @OperLog(module="账目管理",action="导出Excel")
    public ResponseEntity<byte[]> recordExport(@RequestParam(required=false) String recordType, @RequestParam(required=false) String status,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate occurredFrom,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate occurredTo,
            @RequestParam(required=false) Long categoryId, @RequestParam(required=false) String keyword) {
        return file(service.exportRecords(recordType,status,occurredFrom,occurredTo,categoryId,keyword),"bookkeeping-records.xlsx");
    }
    private ResponseEntity<byte[]> file(byte[] bytes, String name) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(XlsxExporter.CONTENT_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(name).build().toString())
                .header(HttpHeaders.CACHE_CONTROL,"no-store").body(bytes);
    }
}
