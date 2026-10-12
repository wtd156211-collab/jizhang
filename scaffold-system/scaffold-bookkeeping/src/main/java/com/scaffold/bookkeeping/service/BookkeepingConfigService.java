package com.scaffold.bookkeeping.service;

import com.scaffold.bookkeeping.domain.dto.CategoryWriteRequest;
import com.scaffold.bookkeeping.domain.dto.FundAccountWriteRequest;
import com.scaffold.bookkeeping.domain.dto.PaymentMethodWriteRequest;
import com.scaffold.bookkeeping.domain.dto.ProjectWriteRequest;
import com.scaffold.bookkeeping.mapper.BookkeepingConfigMapper;
import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.system.domain.vo.IdResponse;
import com.scaffold.framework.datascope.DataScope;
import com.scaffold.framework.datascope.DataScopeContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 记账基础配置服务。配置停用与删除均检查账目引用，保留历史可读性。 */
@Service
public class BookkeepingConfigService {

    private static final List<String> CATEGORY_TYPES = List.of("INCOME", "EXPENSE");

    private final BookkeepingConfigMapper mapper;

    public BookkeepingConfigService(BookkeepingConfigMapper mapper) {
        this.mapper = mapper;
    }

    /** 录入选项：配置仅启用项；用户/部门仅数据范围内的最少字段，无管理权限。 */
    @DataScope
    public Map<String, Object> options() {
        var scope = DataScopeContext.current();
        return Map.of("categories", mapper.selectCategories(null, null, 1, 0, Integer.MAX_VALUE),
                "paymentMethods", mapper.selectPaymentMethods(null, 1, 0, Integer.MAX_VALUE),
                "fundAccounts", mapper.selectFundAccounts(null, 1, 0, Integer.MAX_VALUE),
                "projects", mapper.selectProjects(null, 1, 0, Integer.MAX_VALUE),
                "users", mapper.selectEntryUsers(scope), "depts", mapper.selectEntryDepts(scope));
    }

    public PageResult<Map<String, Object>> categories(PageQuery query, String categoryType, String keyword, Integer status) {
        String normalizedType = nullableUpper(categoryType);
        if (normalizedType != null && !CATEGORY_TYPES.contains(normalizedType)) {
            throw new BusinessException("分类类型只能为收入或支出");
        }
        Page page = pageOf(query);
        return PageResult.of(mapper.selectCategories(normalizedType, trimToNull(keyword), validStatus(status), page.offset(), page.pageSize()),
                mapper.countCategories(normalizedType, trimToNull(keyword), validStatus(status)), page.pageNum(), page.pageSize());
    }

    @Transactional
    public IdResponse createCategory(CategoryWriteRequest request) {
        String type = categoryType(request.getCategoryType());
        String code = code(request.getCategoryCode(), "分类编码不能为空");
        String name = required(request.getCategoryName(), "分类名称不能为空");
        ensureUnique(mapper.countCategoryCode(code, null), "分类编码已存在");
        ensureUnique(mapper.countCategoryName(type, name, null), "分类名称已存在");
        validateCategoryParent(null, request.getParentId(), type);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("categoryType", type);
        row.put("categoryCode", code);
        row.put("categoryName", name);
        row.put("parentId", request.getParentId() == null ? 0L : request.getParentId());
        row.put("sort", sort(request.getSort()));
        row.put("status", status(request.getStatus()));
        mapper.insertCategory(row, currentUsername());
        return new IdResponse(numberAsLong(row.get("id")));
    }

    @Transactional
    public void updateCategory(Long id, CategoryWriteRequest request) {
        requireId(id, "分类 ID 不能为空");
        requireExists(mapper.countCategory(id), "分类不存在");
        String type = categoryType(request.getCategoryType());
        String code = code(request.getCategoryCode(), "分类编码不能为空");
        String name = required(request.getCategoryName(), "分类名称不能为空");
        ensureUnique(mapper.countCategoryCode(code, id), "分类编码已存在");
        ensureUnique(mapper.countCategoryName(type, name, id), "分类名称已存在");
        int nextStatus = status(request.getStatus());
        if (nextStatus == 0 && mapper.countActiveCategoryReferences(id) > 0) {
            throw new BusinessException("分类已被草稿账目使用，不能停用");
        }
        validateCategoryParent(id, request.getParentId(), type);
        if (mapper.countCategoryReferences(id) > 0 || mapper.countCategoryChildren(id) > 0) {
            Map<String, Object> existing = mapper.selectCategory(id);
            if (existing == null || !type.equals(existing.get("categoryType"))) {
                throw new BusinessException("已使用或含下级的分类不能修改收入支出类型");
            }
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("categoryType", type);
        row.put("categoryCode", code);
        row.put("categoryName", name);
        row.put("parentId", request.getParentId() == null ? 0L : request.getParentId());
        row.put("sort", sort(request.getSort()));
        row.put("status", nextStatus);
        if (mapper.updateCategory(id, row, currentUsername()) == 0) {
            throw new BusinessException("分类不存在或已删除");
        }
    }

    @Transactional
    public void deleteCategory(Long id) {
        requireId(id, "分类 ID 不能为空");
        requireExists(mapper.countCategory(id), "分类不存在");
        if (mapper.countCategoryReferences(id) > 0) {
            throw new BusinessException("分类已被历史账目引用，不能删除");
        }
        if (mapper.countCategoryChildren(id) > 0) throw new BusinessException("分类包含下级分类，不能删除");
        if (mapper.softDeleteCategory(id, currentUsername()) == 0) {
            throw new BusinessException("分类不存在或已删除");
        }
    }

    public PageResult<Map<String, Object>> paymentMethods(PageQuery query, String keyword, Integer status) {
        Page page = pageOf(query);
        String normalizedKeyword = trimToNull(keyword);
        Integer normalizedStatus = validStatus(status);
        return PageResult.of(mapper.selectPaymentMethods(normalizedKeyword, normalizedStatus, page.offset(), page.pageSize()),
                mapper.countPaymentMethods(normalizedKeyword, normalizedStatus), page.pageNum(), page.pageSize());
    }

    @Transactional
    public IdResponse createPaymentMethod(PaymentMethodWriteRequest request) {
        String code = code(request.getMethodCode(), "支付方式编码不能为空");
        String name = required(request.getMethodName(), "支付方式名称不能为空");
        ensureUnique(mapper.countPaymentMethodCode(code, null), "支付方式编码已存在");
        ensureUnique(mapper.countPaymentMethodName(name, null), "支付方式名称已存在");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("methodCode", code);
        row.put("methodName", name);
        row.put("sort", sort(request.getSort()));
        row.put("status", status(request.getStatus()));
        mapper.insertPaymentMethod(row, currentUsername());
        return new IdResponse(numberAsLong(row.get("id")));
    }

    @Transactional
    public void updatePaymentMethod(Long id, PaymentMethodWriteRequest request) {
        requireId(id, "支付方式 ID 不能为空");
        requireExists(mapper.countPaymentMethod(id), "支付方式不存在");
        String code = code(request.getMethodCode(), "支付方式编码不能为空");
        String name = required(request.getMethodName(), "支付方式名称不能为空");
        ensureUnique(mapper.countPaymentMethodCode(code, id), "支付方式编码已存在");
        ensureUnique(mapper.countPaymentMethodName(name, id), "支付方式名称已存在");
        int nextStatus = status(request.getStatus());
        if (nextStatus == 0 && mapper.countActivePaymentMethodReferences(id) > 0) {
            throw new BusinessException("支付方式已被草稿账目使用，不能停用");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("methodCode", code);
        row.put("methodName", name);
        row.put("sort", sort(request.getSort()));
        row.put("status", nextStatus);
        if (mapper.updatePaymentMethod(id, row, currentUsername()) == 0) {
            throw new BusinessException("支付方式不存在或已删除");
        }
    }

    @Transactional
    public void deletePaymentMethod(Long id) {
        requireId(id, "支付方式 ID 不能为空");
        requireExists(mapper.countPaymentMethod(id), "支付方式不存在");
        if (mapper.countPaymentMethodReferences(id) > 0) {
            throw new BusinessException("支付方式已被历史账目引用，不能删除");
        }
        if (mapper.softDeletePaymentMethod(id, currentUsername()) == 0) {
            throw new BusinessException("支付方式不存在或已删除");
        }
    }

    public PageResult<Map<String, Object>> fundAccounts(PageQuery query, String keyword, Integer status) {
        Page page = pageOf(query);
        String normalizedKeyword = trimToNull(keyword);
        Integer normalizedStatus = validStatus(status);
        return PageResult.of(mapper.selectFundAccounts(normalizedKeyword, normalizedStatus, page.offset(), page.pageSize()),
                mapper.countFundAccounts(normalizedKeyword, normalizedStatus), page.pageNum(), page.pageSize());
    }

    @Transactional
    public IdResponse createFundAccount(FundAccountWriteRequest request) {
        String code = code(request.getAccountCode(), "资金账户编码不能为空");
        String name = required(request.getAccountName(), "资金账户名称不能为空");
        String type = code(request.getAccountType(), "账户类型不能为空");
        ensureUnique(mapper.countFundAccountCode(code, null), "资金账户编码已存在");
        ensureUnique(mapper.countFundAccountName(name, null), "资金账户名称已存在");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("accountCode", code);
        row.put("accountName", name);
        row.put("accountType", type);
        row.put("sort", sort(request.getSort()));
        row.put("status", status(request.getStatus()));
        row.put("remark", trimToNull(request.getRemark()));
        mapper.insertFundAccount(row, currentUsername());
        return new IdResponse(numberAsLong(row.get("id")));
    }

    @Transactional
    public void updateFundAccount(Long id, FundAccountWriteRequest request) {
        requireId(id, "资金账户 ID 不能为空");
        requireExists(mapper.countFundAccount(id), "资金账户不存在");
        String code = code(request.getAccountCode(), "资金账户编码不能为空");
        String name = required(request.getAccountName(), "资金账户名称不能为空");
        String type = code(request.getAccountType(), "账户类型不能为空");
        ensureUnique(mapper.countFundAccountCode(code, id), "资金账户编码已存在");
        ensureUnique(mapper.countFundAccountName(name, id), "资金账户名称已存在");
        int nextStatus = status(request.getStatus());
        if (nextStatus == 0 && mapper.countActiveFundAccountReferences(id) > 0) {
            throw new BusinessException("资金账户已被草稿账目使用，不能停用");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("accountCode", code);
        row.put("accountName", name);
        row.put("accountType", type);
        row.put("sort", sort(request.getSort()));
        row.put("status", nextStatus);
        row.put("remark", trimToNull(request.getRemark()));
        if (mapper.updateFundAccount(id, row, currentUsername()) == 0) {
            throw new BusinessException("资金账户不存在或已删除");
        }
    }

    @Transactional
    public void deleteFundAccount(Long id) {
        requireId(id, "资金账户 ID 不能为空");
        requireExists(mapper.countFundAccount(id), "资金账户不存在");
        if (mapper.countFundAccountReferences(id) > 0) {
            throw new BusinessException("资金账户已被历史账目引用，不能删除");
        }
        if (mapper.softDeleteFundAccount(id, currentUsername()) == 0) {
            throw new BusinessException("资金账户不存在或已删除");
        }
    }

    public PageResult<Map<String, Object>> projects(PageQuery query, String keyword, Integer status) {
        Page page = pageOf(query);
        String normalizedKeyword = trimToNull(keyword);
        Integer normalizedStatus = validStatus(status);
        return PageResult.of(mapper.selectProjects(normalizedKeyword, normalizedStatus, page.offset(), page.pageSize()),
                mapper.countProjects(normalizedKeyword, normalizedStatus), page.pageNum(), page.pageSize());
    }

    @Transactional
    public IdResponse createProject(ProjectWriteRequest request) {
        String code = code(request.getProjectCode(), "项目编码不能为空");
        String name = required(request.getProjectName(), "项目名称不能为空");
        ensureUnique(mapper.countProjectCode(code, null), "项目编码已存在");
        ensureUnique(mapper.countProjectName(name, null), "项目名称已存在");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("projectCode", code);
        row.put("projectName", name);
        row.put("sort", sort(request.getSort()));
        row.put("status", status(request.getStatus()));
        row.put("remark", trimToNull(request.getRemark()));
        mapper.insertProject(row, currentUsername());
        return new IdResponse(numberAsLong(row.get("id")));
    }

    @Transactional
    public void updateProject(Long id, ProjectWriteRequest request) {
        requireId(id, "项目 ID 不能为空");
        requireExists(mapper.countProject(id), "项目不存在");
        String code = code(request.getProjectCode(), "项目编码不能为空");
        String name = required(request.getProjectName(), "项目名称不能为空");
        ensureUnique(mapper.countProjectCode(code, id), "项目编码已存在");
        ensureUnique(mapper.countProjectName(name, id), "项目名称已存在");
        int nextStatus = status(request.getStatus());
        if (nextStatus == 0 && mapper.countActiveProjectReferences(id) > 0) {
            throw new BusinessException("项目已被草稿账目使用，不能停用");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("projectCode", code);
        row.put("projectName", name);
        row.put("sort", sort(request.getSort()));
        row.put("status", nextStatus);
        row.put("remark", trimToNull(request.getRemark()));
        if (mapper.updateProject(id, row, currentUsername()) == 0) {
            throw new BusinessException("项目不存在或已删除");
        }
    }

    @Transactional
    public void deleteProject(Long id) {
        requireId(id, "项目 ID 不能为空");
        requireExists(mapper.countProject(id), "项目不存在");
        if (mapper.countProjectReferences(id) > 0) {
            throw new BusinessException("项目已被历史账目引用，不能删除");
        }
        if (mapper.softDeleteProject(id, currentUsername()) == 0) {
            throw new BusinessException("项目不存在或已删除");
        }
    }

    private String categoryType(String value) {
        String type = nullableUpper(value);
        if (type == null || !CATEGORY_TYPES.contains(type)) {
            throw new BusinessException("分类类型只能为收入或支出");
        }
        return type;
    }

    private void validateCategoryParent(Long id, Long parentId, String type) {
        if (parentId == null || parentId == 0) return;
        if (parentId < 0) throw new BusinessException("上级分类不正确");
        var visited = new HashSet<Long>();
        if (id != null) visited.add(id);
        Long cursor = parentId;
        while (cursor != null && cursor != 0) {
            if (!visited.add(cursor) || visited.size() > 100) throw new BusinessException("分类层级不能形成循环");
            Map<String, Object> parent = mapper.selectCategory(cursor);
            if (parent == null) throw new BusinessException("上级分类不存在或已删除");
            if (!type.equals(parent.get("categoryType"))) throw new BusinessException("上级分类必须与当前分类类型一致");
            Object next = parent.get("parentId");
            cursor = next instanceof Number number ? number.longValue() : 0L;
        }
    }

    private String code(String value, String message) {
        String normalized = required(value, message).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_-]{1,64}")) {
            throw new BusinessException("编码只能包含字母、数字、下划线或短横线");
        }
        return normalized;
    }

    private int sort(Integer value) {
        if (value == null || value < 0 || value > 9999) {
            throw new BusinessException("排序号必须在0到9999之间");
        }
        return value;
    }

    private int status(Integer value) {
        if (value == null || (value != 0 && value != 1)) {
            throw new BusinessException("状态只能为正常或停用");
        }
        return value;
    }

    private Integer validStatus(Integer value) {
        if (value != null && value != 0 && value != 1) {
            throw new BusinessException("状态只能为正常或停用");
        }
        return value;
    }

    private void ensureUnique(long count, String message) {
        if (count > 0) {
            throw new BusinessException(message);
        }
    }

    private void requireExists(long count, String message) {
        if (count == 0) {
            throw new BusinessException(message);
        }
    }

    private void requireId(Long id, String message) {
        if (id == null || id < 1) {
            throw new BusinessException(message);
        }
    }

    private String required(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String nullableUpper(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private long numberAsLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : String.valueOf(authentication.getPrincipal());
    }

    private Page pageOf(PageQuery query) {
        PageQuery effective = query == null ? new PageQuery(null, null, null, null) : query;
        int pageNum = effective.normalizedPageNum();
        int pageSize = effective.normalizedPageSize();
        return new Page(pageNum, pageSize, (pageNum - 1) * pageSize);
    }

    private record Page(int pageNum, int pageSize, int offset) {
    }
}
