package com.scaffold.bookkeeping.controller;

import com.scaffold.bookkeeping.domain.dto.CategoryWriteRequest;
import com.scaffold.bookkeeping.domain.dto.FundAccountWriteRequest;
import com.scaffold.bookkeeping.domain.dto.PaymentMethodWriteRequest;
import com.scaffold.bookkeeping.domain.dto.ProjectWriteRequest;
import com.scaffold.bookkeeping.service.BookkeepingConfigService;
import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.domain.R;
import com.scaffold.framework.audit.OperLog;
import com.scaffold.framework.security.RequiresPermission;
import com.scaffold.system.domain.vo.IdResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 记账基础配置接口。字段返回约定：id 主键、code 编码、name 名称、status 状态、createTime 创建时间。 */
@RestController
@RequestMapping("/api/bookkeeping/config")
public class BookkeepingConfigController {
    @GetMapping("/options")
    @RequiresPermission("bookkeeping:record:list")
    public R<Map<String, Object>> options() { return R.success(service.options()); }

    private static final String LIST_PERMISSION = "bookkeeping:config:list";
    private static final String EDIT_PERMISSION = "bookkeeping:config:edit";

    private final BookkeepingConfigService service;

    public BookkeepingConfigController(BookkeepingConfigService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    @RequiresPermission(LIST_PERMISSION)
    public R<PageResult<Map<String, Object>>> categories(
            PageQuery query,
            @RequestParam(required = false) String categoryType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status
    ) {
        return R.success(service.categories(query, categoryType, keyword, status));
    }

    @PostMapping("/categories")
    @RequiresPermission(EDIT_PERMISSION)
    @OperLog(module = "记账分类", action = "新增")
    public R<IdResponse> createCategory(@Valid @RequestBody CategoryWriteRequest request) {
        return R.success(service.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    @RequiresPermission(EDIT_PERMISSION)
    @OperLog(module = "记账分类", action = "修改")
    public R<Void> updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryWriteRequest request) {
        service.updateCategory(id, request);
        return R.success(null);
    }

    @DeleteMapping("/categories/{id}")
    @RequiresPermission(EDIT_PERMISSION)
    @OperLog(module = "记账分类", action = "删除")
    public R<Void> deleteCategory(@PathVariable Long id) {
        service.deleteCategory(id);
        return R.success(null);
    }

    @GetMapping("/payment-methods")
    @RequiresPermission(LIST_PERMISSION)
    public R<PageResult<Map<String, Object>>> paymentMethods(
            PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status
    ) {
        return R.success(service.paymentMethods(query, keyword, status));
    }

    @PostMapping("/payment-methods")
    @RequiresPermission(EDIT_PERMISSION)
    @OperLog(module = "支付方式", action = "新增")
    public R<IdResponse> createPaymentMethod(@Valid @RequestBody PaymentMethodWriteRequest request) {
        return R.success(service.createPaymentMethod(request));
    }

    @PutMapping("/payment-methods/{id}")
    @RequiresPermission(EDIT_PERMISSION)
    @OperLog(module = "支付方式", action = "修改")
    public R<Void> updatePaymentMethod(@PathVariable Long id, @Valid @RequestBody PaymentMethodWriteRequest request) {
        service.updatePaymentMethod(id, request);
        return R.success(null);
    }

    @DeleteMapping("/payment-methods/{id}")
    @RequiresPermission(EDIT_PERMISSION)
    @OperLog(module = "支付方式", action = "删除")
    public R<Void> deletePaymentMethod(@PathVariable Long id) {
        service.deletePaymentMethod(id);
        return R.success(null);
    }

    @GetMapping("/fund-accounts")
    @RequiresPermission(LIST_PERMISSION)
    public R<PageResult<Map<String, Object>>> fundAccounts(
            PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status
    ) {
        return R.success(service.fundAccounts(query, keyword, status));
    }

    @PostMapping("/fund-accounts")
    @RequiresPermission(EDIT_PERMISSION)
    @OperLog(module = "资金账户", action = "新增")
    public R<IdResponse> createFundAccount(@Valid @RequestBody FundAccountWriteRequest request) {
        return R.success(service.createFundAccount(request));
    }

    @PutMapping("/fund-accounts/{id}")
    @RequiresPermission(EDIT_PERMISSION)
    @OperLog(module = "资金账户", action = "修改")
    public R<Void> updateFundAccount(@PathVariable Long id, @Valid @RequestBody FundAccountWriteRequest request) {
        service.updateFundAccount(id, request);
        return R.success(null);
    }

    @DeleteMapping("/fund-accounts/{id}")
    @RequiresPermission(EDIT_PERMISSION)
    @OperLog(module = "资金账户", action = "删除")
    public R<Void> deleteFundAccount(@PathVariable Long id) {
        service.deleteFundAccount(id);
        return R.success(null);
    }

    @GetMapping("/projects")
    @RequiresPermission(LIST_PERMISSION)
    public R<PageResult<Map<String, Object>>> projects(
            PageQuery query,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status
    ) {
        return R.success(service.projects(query, keyword, status));
    }

    @PostMapping("/projects")
    @RequiresPermission(EDIT_PERMISSION)
    @OperLog(module = "记账项目", action = "新增")
    public R<IdResponse> createProject(@Valid @RequestBody ProjectWriteRequest request) {
        return R.success(service.createProject(request));
    }

    @PutMapping("/projects/{id}")
    @RequiresPermission(EDIT_PERMISSION)
    @OperLog(module = "记账项目", action = "修改")
    public R<Void> updateProject(@PathVariable Long id, @Valid @RequestBody ProjectWriteRequest request) {
        service.updateProject(id, request);
        return R.success(null);
    }

    @DeleteMapping("/projects/{id}")
    @RequiresPermission(EDIT_PERMISSION)
    @OperLog(module = "记账项目", action = "删除")
    public R<Void> deleteProject(@PathVariable Long id) {
        service.deleteProject(id);
        return R.success(null);
    }
}
