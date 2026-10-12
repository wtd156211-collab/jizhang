package com.scaffold.bookkeeping.controller;

import com.scaffold.bookkeeping.service.AttachmentService;
import com.scaffold.common.domain.R;
import com.scaffold.framework.audit.OperLog;
import com.scaffold.framework.security.RequiresPermission;
import com.scaffold.system.domain.vo.IdResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 票据附件接口。所有元数据和文件读取均校验附件权限与账目数据范围。 */
@RestController
@RequestMapping("/api/bookkeeping")
public class AttachmentController {
    private final AttachmentService service;

    public AttachmentController(AttachmentService service) { this.service = service; }

    @PostMapping(value = "/records/{recordId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("bookkeeping:attachment:upload")
    @OperLog(module = "票据附件", action = "上传")
    public R<IdResponse> upload(@PathVariable Long recordId, @RequestParam MultipartFile file,
                               @RequestParam(defaultValue = "false") boolean primary) {
        return R.success(service.upload(recordId, file, primary));
    }

    @GetMapping("/records/{recordId}/attachments")
    @RequiresPermission("bookkeeping:attachment:view")
    public R<List<Map<String, Object>>> list(@PathVariable Long recordId) {
        return R.success(service.list(recordId));
    }

    @GetMapping("/attachments/{id}/download")
    @RequiresPermission("bookkeeping:attachment:view")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        AttachmentService.Download file = service.download(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.mimeType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.originalName(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(file.path()));
    }

    @PutMapping("/attachments/{id}/primary")
    @RequiresPermission("bookkeeping:attachment:upload")
    @OperLog(module = "票据附件", action = "设置主票据")
    public R<Void> setPrimary(@PathVariable Long id) {
        service.setPrimary(id);
        return R.success(null);
    }

    @DeleteMapping("/attachments/{id}")
    @RequiresPermission("bookkeeping:attachment:remove")
    @OperLog(module = "票据附件", action = "删除")
    public R<Void> remove(@PathVariable Long id) {
        service.remove(id);
        return R.success(null);
    }
}
