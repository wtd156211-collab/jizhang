package com.scaffold.bookkeeping.controller;

import com.scaffold.bookkeeping.domain.dto.*;
import com.scaffold.bookkeeping.domain.vo.*;
import com.scaffold.bookkeeping.service.*;
import com.scaffold.common.domain.R;
import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.framework.audit.OperLog;
import com.scaffold.framework.security.RequiresPermission;
import com.scaffold.system.domain.vo.IdResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** OCR 配置采用独立管理权限；识别/确认还在服务层检查附件与账目编辑权限。 */
@RestController
@RequestMapping("/api/bookkeeping")
public class OcrController {
    private final OcrService tasks;
    private final OcrConfigService config;
    public OcrController(OcrService tasks, OcrConfigService config) { this.tasks=tasks; this.config=config; }

    @PostMapping("/records/{id}/ocr")
    @RequiresPermission("bookkeeping:ocr:recognize")
    @OperLog(module="票据识别", action="识别")
    public R<IdResponse> start(@PathVariable Long id, @Valid @RequestBody OcrStartRequest request) { return R.success(tasks.recognize(id,request)); }

    @GetMapping("/ocr/tasks/{id}")
    @RequiresPermission("bookkeeping:ocr:recognize")
    public ResponseEntity<R<OcrTaskVO>> task(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(R.success(tasks.get(id)));
    }

    @GetMapping("/records/{id}/ocr/tasks")
    @RequiresPermission("bookkeeping:ocr:recognize")
    public ResponseEntity<R<PageResult<OcrTaskVO>>> taskList(@PathVariable Long id, PageQuery query) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(R.success(tasks.list(id,query)));
    }

    @PostMapping("/ocr/tasks/{id}/confirm")
    @RequiresPermission("bookkeeping:ocr:recognize")
    @OperLog(module="票据识别", action="人工确认")
    public R<Void> confirm(@PathVariable Long id, @Valid @RequestBody RecordWriteRequest request) { tasks.confirm(id,request); return R.success(null); }

    @GetMapping("/ocr/config/status")
    @RequiresPermission("bookkeeping:ocr:config")
    public ResponseEntity<R<OcrConfigStatusVO>> configStatus() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(R.success(config.status()));
    }

    @PutMapping("/ocr/config")
    @RequiresPermission("bookkeeping:ocr:config")
    @OperLog(module="票据识别", action="保存配置")
    public R<Void> saveConfig(@Valid @RequestBody OcrConfigRequest request) { config.save(request); return R.success(null); }
}
