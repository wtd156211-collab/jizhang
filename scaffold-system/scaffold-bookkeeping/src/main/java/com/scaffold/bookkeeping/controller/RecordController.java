package com.scaffold.bookkeeping.controller;

import com.scaffold.bookkeeping.domain.dto.RecordWriteRequest;
import com.scaffold.bookkeeping.domain.vo.RecordResponse;
import com.scaffold.bookkeeping.service.RecordService;
import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.domain.R;
import com.scaffold.framework.audit.OperLog;
import com.scaffold.framework.security.RequiresPermission;
import com.scaffold.system.domain.vo.IdResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/** 账目草稿接口。账目列表按实际发生日期和可用的实际发生时间排序。 */
@RestController
@RequestMapping("/api/bookkeeping/records")
public class RecordController {

    private final RecordService service;

    public RecordController(RecordService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission("bookkeeping:record:list")
    public R<PageResult<Map<String, Object>>> list(
            PageQuery query,
            @RequestParam(required = false) String recordType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurredTo,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword
    ) {
        return R.success(RecordResponse.page(service.list(query, recordType, status, occurredFrom, occurredTo, categoryId, keyword)));
    }

    @PostMapping
    @RequiresPermission("bookkeeping:record:add")
    @OperLog(module = "账目管理", action = "新增草稿")
    public R<IdResponse> create(@Valid @RequestBody RecordWriteRequest request) {
        return R.success(service.create(request));
    }

    @GetMapping("/{id}")
    @RequiresPermission("bookkeeping:record:list")
    public R<Map<String, Object>> get(@PathVariable Long id) {
        return R.success(RecordResponse.record(service.get(id)));
    }

    @PutMapping("/{id}")
    @RequiresPermission("bookkeeping:record:edit")
    @OperLog(module = "账目管理", action = "修改草稿")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody RecordWriteRequest request) {
        service.update(id, request);
        return R.success(null);
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("bookkeeping:record:edit")
    @OperLog(module = "账目管理", action = "删除草稿")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.success(null);
    }
}
