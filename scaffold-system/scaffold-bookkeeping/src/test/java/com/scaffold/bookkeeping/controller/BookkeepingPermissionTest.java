package com.scaffold.bookkeeping.controller;

import com.scaffold.bookkeeping.service.AttachmentService;
import com.scaffold.bookkeeping.service.BookkeepingConfigService;
import com.scaffold.bookkeeping.service.ReviewService;
import com.scaffold.bookkeeping.service.RecordHistoryService;
import com.scaffold.bookkeeping.service.OcrService;
import com.scaffold.bookkeeping.service.OcrConfigService;
import com.scaffold.framework.security.PermissionAspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 使用真实权限切面调用业务 Controller，不启动数据库或写入本地开发库。 */
class BookkeepingPermissionTest {
    @AfterEach
    void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test
    void recordEditPermissionDoesNotGrantAttachmentAccess() {
        var controller = secure(new AttachmentController(mock(AttachmentService.class)), "bookkeeping:record:edit");

        assertAll(
                () -> assertThrows(AccessDeniedException.class, () -> controller.list(1L)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.upload(1L, null, false)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.download(1L)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.setPrimary(1L)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.remove(1L))
        );
    }

    @Test
    void readOnlyAttachmentPermissionCannotChangeFiles() {
        var controller = secure(new AttachmentController(mock(AttachmentService.class)), "bookkeeping:attachment:view");

        assertDoesNotThrow(() -> controller.list(1L));
        assertThrows(AccessDeniedException.class, () -> controller.upload(1L, null, false));
        assertThrows(AccessDeniedException.class, () -> controller.setPrimary(1L));
        assertThrows(AccessDeniedException.class, () -> controller.remove(1L));
    }

    @Test
    void configListPermissionCannotWriteOrDeleteConfiguration() {
        var controller = secure(new BookkeepingConfigController(mock(BookkeepingConfigService.class)), "bookkeeping:config:list");

        assertDoesNotThrow(() -> controller.categories(null, null, null, null));
        assertAll(
                () -> assertThrows(AccessDeniedException.class, () -> controller.createCategory(null)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.updateCategory(1L, null)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.deleteCategory(1L)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.createPaymentMethod(null)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.updatePaymentMethod(1L, null)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.deletePaymentMethod(1L)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.createFundAccount(null)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.updateFundAccount(1L, null)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.deleteFundAccount(1L)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.createProject(null)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.updateProject(1L, null)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.deleteProject(1L))
        );
    }

    @Test
    void recordListCanReadEntryOptionsWithoutManagingConfiguration() {
        var controller = secure(new BookkeepingConfigController(mock(BookkeepingConfigService.class)), "bookkeeping:record:list");
        assertDoesNotThrow(controller::options);
        assertThrows(AccessDeniedException.class, () -> controller.categories(null, null, null, null));
        assertThrows(AccessDeniedException.class, () -> controller.createCategory(null));
        var unrelated = secure(new BookkeepingConfigController(mock(BookkeepingConfigService.class)), "system:user:list");
        assertThrows(AccessDeniedException.class, unrelated::options);
    }

    @Test
    void recordEditDoesNotGrantReviewOrHistoryAccess() {
        var controller = secure(new ReviewController(mock(ReviewService.class), mock(RecordHistoryService.class)), "bookkeeping:record:edit");
        assertAll(
                () -> assertThrows(AccessDeniedException.class, () -> controller.submit(1L, null)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.approve(1L, null)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.returnRecord(1L, null)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.withdraw(1L, null)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.voidRecord(1L, null)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.reviews(1L, null)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.versions(1L, null)),
                () -> assertThrows(AccessDeniedException.class, () -> controller.pending(null, null, null, null, null, null))
        );
    }

    @Test
    void approvalPermissionCannotReturnWithdrawOrVoidAndHistoryUsesRecordReadPermission() {
        var controller = secure(new ReviewController(mock(ReviewService.class), mock(RecordHistoryService.class)), "bookkeeping:record:review");
        assertDoesNotThrow(() -> controller.approve(1L, null));
        assertThrows(AccessDeniedException.class, () -> controller.returnRecord(1L, null));
        assertThrows(AccessDeniedException.class, () -> controller.withdraw(1L, null));
        assertThrows(AccessDeniedException.class, () -> controller.voidRecord(1L, null));
        var readOnly = secure(new ReviewController(mock(ReviewService.class), mock(RecordHistoryService.class)), "bookkeeping:record:list");
        assertDoesNotThrow(() -> readOnly.reviews(1L, null));
        assertDoesNotThrow(() -> readOnly.versions(1L, null));
        assertThrows(AccessDeniedException.class, () -> readOnly.approve(1L, null));
    }

    @Test
    void ocrRecognitionPermissionCannotManageSecretsAndEditCannotReadOcrTasks() {
        var ocr = secure(new OcrController(mock(OcrService.class), mock(OcrConfigService.class)), "bookkeeping:ocr:recognize");
        assertDoesNotThrow(() -> ocr.start(1L, null));
        assertDoesNotThrow(() -> ocr.task(1L));
        assertThrows(AccessDeniedException.class, () -> ocr.configStatus());
        assertThrows(AccessDeniedException.class, () -> ocr.saveConfig(null));
        var edit = secure(new OcrController(mock(OcrService.class), mock(OcrConfigService.class)), "bookkeeping:record:edit");
        assertThrows(AccessDeniedException.class, () -> edit.start(1L,null));
        assertThrows(AccessDeniedException.class, () -> edit.confirm(1L,null));
    }

    private <T> T secure(T target, String grantedPermission) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("clerk", "", List.of()));
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(new PermissionAspect((username, permission) -> permission.equals(grantedPermission)));
        return factory.getProxy();
    }
}
