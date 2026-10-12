package com.scaffold.bookkeeping.controller;

import com.scaffold.bookkeeping.domain.ReviewAction;
import com.scaffold.bookkeeping.domain.dto.ReviewActionRequest;
import com.scaffold.bookkeeping.domain.vo.RecordVersionVO;
import com.scaffold.bookkeeping.domain.vo.RecordResponse;
import com.scaffold.bookkeeping.domain.vo.ReviewHistoryVO;
import com.scaffold.bookkeeping.service.RecordHistoryService;
import com.scaffold.bookkeeping.service.ReviewService;
import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.domain.R;
import com.scaffold.framework.audit.OperLog;
import com.scaffold.framework.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/** 复用统一响应、权限和操作日志；状态动作始终从固定路由映射，客户端不能指定目标状态。 */
@RestController
@RequestMapping("/api/bookkeeping")
public class ReviewController {
    private final ReviewService service;
    private final RecordHistoryService history;

    public ReviewController(ReviewService service, RecordHistoryService history) {
        this.service = service;
        this.history = history;
    }

    @GetMapping("/reviews/pending")
    @RequiresPermission("bookkeeping:review:list")
    public R<PageResult<Map<String, Object>>> pending(PageQuery query,
            @RequestParam(required = false) String recordType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurredFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate occurredTo,
            @RequestParam(required = false) Long categoryId, @RequestParam(required = false) String keyword) {
        return R.success(RecordResponse.page(service.pending(query, recordType, occurredFrom, occurredTo, categoryId, keyword)));
    }

    @PostMapping("/records/{id}/submit")
    @RequiresPermission("bookkeeping:record:submit")
    @OperLog(module = "账目审核", action = "提交审核")
    public R<Void> submit(@PathVariable Long id, @Valid @RequestBody ReviewActionRequest request) {
        service.act(id, ReviewAction.SUBMIT, request);
        return R.success(null);
    }

    @PostMapping("/records/{id}/review/approve")
    @RequiresPermission("bookkeeping:record:review")
    @OperLog(module = "账目审核", action = "审核通过")
    public R<Void> approve(@PathVariable Long id, @Valid @RequestBody ReviewActionRequest request) {
        service.act(id, ReviewAction.APPROVE, request);
        return R.success(null);
    }

    @PostMapping("/records/{id}/review/return")
    @RequiresPermission("bookkeeping:record:return")
    @OperLog(module = "账目审核", action = "退回")
    public R<Void> returnRecord(@PathVariable Long id, @Valid @RequestBody ReviewActionRequest request) {
        service.act(id, ReviewAction.RETURN, request);
        return R.success(null);
    }

    @PostMapping("/records/{id}/withdraw")
    @RequiresPermission("bookkeeping:record:withdraw")
    @OperLog(module = "账目审核", action = "撤回重审")
    public R<Void> withdraw(@PathVariable Long id, @Valid @RequestBody ReviewActionRequest request) {
        service.act(id, ReviewAction.WITHDRAW, request);
        return R.success(null);
    }

    @PostMapping("/records/{id}/void")
    @RequiresPermission("bookkeeping:record:void")
    @OperLog(module = "账目审核", action = "作废")
    public R<Void> voidRecord(@PathVariable Long id, @Valid @RequestBody ReviewActionRequest request) {
        service.act(id, ReviewAction.VOID, request);
        return R.success(null);
    }

    @GetMapping("/records/{id}/reviews")
    @RequiresPermission("bookkeeping:record:list")
    public R<PageResult<ReviewHistoryVO>> reviews(@PathVariable Long id, PageQuery query) {
        return R.success(history.reviews(id, query));
    }

    @GetMapping("/records/{id}/versions")
    @RequiresPermission("bookkeeping:record:list")
    public R<PageResult<RecordVersionVO>> versions(@PathVariable Long id, PageQuery query) {
        return R.success(history.versions(id, query));
    }
}
