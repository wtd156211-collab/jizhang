package com.scaffold.bookkeeping.service;

import com.scaffold.bookkeeping.domain.dto.CategoryWriteRequest;
import com.scaffold.bookkeeping.mapper.BookkeepingConfigMapper;
import com.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookkeepingConfigServiceTest {

    @Mock
    private BookkeepingConfigMapper mapper;

    @InjectMocks
    private BookkeepingConfigService service;

    @Test
    void createsCategoryAfterValidatingTypeAndCode() {
        CategoryWriteRequest request = new CategoryWriteRequest();
        request.setCategoryType("expense");
        request.setCategoryCode("office");
        request.setCategoryName("办公费用");
        request.setSort(2);
        request.setStatus(1);
        when(mapper.countCategoryCode("OFFICE", null)).thenReturn(0L);
        when(mapper.insertCategory(any(), eq("system"))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            var values = (java.util.Map<String, Object>) invocation.getArgument(0);
            values.put("id", 11L);
            return 1;
        });

        var result = service.createCategory(request);

        assertEquals(11L, result.id());
        verify(mapper).insertCategory(any(), eq("system"));
    }

    @Test
    void rejectsDuplicateCategoryCode() {
        CategoryWriteRequest request = new CategoryWriteRequest();
        request.setCategoryType("INCOME");
        request.setCategoryCode("sales");
        request.setCategoryName("销售收入");
        when(mapper.countCategoryCode("SALES", null)).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.createCategory(request));

        assertEquals("分类编码已存在", error.getMessage());
    }

    @Test
    void rejectsDuplicateCategoryNameWithinSameType() {
        CategoryWriteRequest request = new CategoryWriteRequest();
        request.setCategoryType("EXPENSE");
        request.setCategoryCode("OFFICE_2");
        request.setCategoryName("办公费用");
        when(mapper.countCategoryCode("OFFICE_2", null)).thenReturn(0L);
        when(mapper.countCategoryName("EXPENSE", "办公费用", null)).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.createCategory(request));

        assertEquals("分类名称已存在", error.getMessage());
    }

    @Test
    void preventsDisablingCategoryUsedByDraft() {
        CategoryWriteRequest request = new CategoryWriteRequest();
        request.setCategoryType("EXPENSE");
        request.setCategoryCode("OFFICE");
        request.setCategoryName("办公费用");
        request.setSort(1);
        request.setStatus(0);
        when(mapper.countCategory(3L)).thenReturn(1L);
        when(mapper.countCategoryCode("OFFICE", 3L)).thenReturn(0L);
        when(mapper.countActiveCategoryReferences(3L)).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.updateCategory(3L, request));

        assertEquals("分类已被草稿账目使用，不能停用", error.getMessage());
    }

    @Test
    void rejectsParentOfAnotherCategoryType() {
        CategoryWriteRequest request = validCategory();
        request.setParentId(2L);
        when(mapper.selectCategory(2L)).thenReturn(Map.of("id", 2L, "categoryType", "INCOME", "parentId", 0L));

        BusinessException error = assertThrows(BusinessException.class, () -> service.createCategory(request));
        assertEquals("上级分类必须与当前分类类型一致", error.getMessage());
    }

    @Test
    void rejectsCategoryHierarchyCycle() {
        CategoryWriteRequest request = validCategory();
        request.setParentId(5L);
        when(mapper.countCategory(3L)).thenReturn(1L);
        when(mapper.selectCategory(5L)).thenReturn(Map.of("id", 5L, "categoryType", "EXPENSE", "parentId", 3L));

        BusinessException error = assertThrows(BusinessException.class, () -> service.updateCategory(3L, request));
        assertEquals("分类层级不能形成循环", error.getMessage());
    }

    @Test
    void preventsChangingTypeOfHistoricallyUsedCategory() {
        CategoryWriteRequest request = validCategory();
        request.setCategoryType("INCOME");
        when(mapper.countCategory(3L)).thenReturn(1L);
        when(mapper.countCategoryReferences(3L)).thenReturn(1L);
        when(mapper.selectCategory(3L)).thenReturn(Map.of("id", 3L, "categoryType", "EXPENSE", "parentId", 0L));

        BusinessException error = assertThrows(BusinessException.class, () -> service.updateCategory(3L, request));
        assertEquals("已使用或含下级的分类不能修改收入支出类型", error.getMessage());
    }

    @Test
    void preventsDeletingCategoryWithChildren() {
        when(mapper.countCategory(3L)).thenReturn(1L);
        when(mapper.countCategoryChildren(3L)).thenReturn(1L);

        BusinessException error = assertThrows(BusinessException.class, () -> service.deleteCategory(3L));
        assertEquals("分类包含下级分类，不能删除", error.getMessage());
    }

    private CategoryWriteRequest validCategory() {
        CategoryWriteRequest request = new CategoryWriteRequest();
        request.setCategoryType("EXPENSE");
        request.setCategoryCode("OFFICE");
        request.setCategoryName("办公费用");
        request.setSort(1);
        request.setStatus(1);
        return request;
    }
}
