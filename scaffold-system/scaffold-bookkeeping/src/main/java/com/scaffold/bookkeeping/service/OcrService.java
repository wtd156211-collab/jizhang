package com.scaffold.bookkeeping.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scaffold.bookkeeping.domain.dto.OcrStartRequest;
import com.scaffold.bookkeeping.domain.dto.RecordWriteRequest;
import com.scaffold.bookkeeping.domain.vo.OcrTaskVO;
import com.scaffold.bookkeeping.mapper.*;
import com.scaffold.bookkeeping.ocr.AliyunOcrProvider;
import com.scaffold.bookkeeping.storage.AttachmentStorage;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.framework.datascope.DataScope;
import com.scaffold.framework.datascope.DataScopeContext;
import com.scaffold.framework.security.PermissionResolver;
import com.scaffold.system.domain.vo.IdResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** 首版同步识别，云请求有超时；账目主表锁统一串行化附件、识别与审核动作。 */
@Service
public class OcrService {
    private static final Set<String> EDITABLE = Set.of("DRAFT", "RETURNED", "WITHDRAWN");
    private static final Set<String> FIELDS = Set.of("summary", "occurredDate", "occurredTime", "totalAmount", "text");
    private final OcrTaskMapper tasks;
    private final RecordMapper records;
    private final AttachmentMapper attachments;
    private final AttachmentStorage storage;
    private final OcrConfigService configs;
    private final RecordService service;
    private final ObjectMapper json;
    private final PermissionResolver permissions;
    public OcrService(OcrTaskMapper tasks, RecordMapper records, AttachmentMapper attachments, AttachmentStorage storage,
                      OcrConfigService configs, RecordService service, ObjectMapper json, PermissionResolver permissions) {
        this.tasks=tasks; this.records=records; this.attachments=attachments; this.storage=storage;
        this.configs=configs; this.service=service; this.json=json; this.permissions=permissions;
    }

    @DataScope
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public IdResponse recognize(Long recordId, OcrStartRequest request) {
        requirePermission("bookkeeping:attachment:view");
        if (request == null || request.getAttachmentId() == null || request.getAttachmentId() < 1 || request.getVersion() == null
                || request.getVersion() < 0 || !AliyunOcrProvider.TYPES.contains(request.getDocumentType())) {
            throw new BusinessException("OCR 参数不正确");
        }
        var record = requireRecord(recordId, true);
        requireVersion(record, request.getVersion());
        var attachment = requireAttachment(request.getAttachmentId(), recordId);
        Long userId = records.selectUserIdByUsername(username());
        if (userId == null) throw new BusinessException(401, "登录已失效");
        String provider = configs.providerName();
        Long recent = tasks.recentSuccess(recordId, request.getAttachmentId(), userId, provider, request.getDocumentType(), request.getVersion());
        if (recent != null) return new IdResponse(recent);
        Map<String,Object> row = new LinkedHashMap<>();
        row.put("recordId",recordId); row.put("attachmentId",request.getAttachmentId()); row.put("provider",provider); row.put("requestedBy",userId);
        tasks.insertTask(row); Long id = number(row, "id");
        String status = "FAILED", error = "OCR 识别失败，请检查配置或票据类型，可继续人工录入", raw = null, normalized = null;
        try {
            Path path = storage.load(String.valueOf(attachment.get("storageKey")));
            if (!Files.isRegularFile(path)) throw new IllegalStateException("Original unavailable");
            var runtime = configs.runtime(); provider = runtime.provider().id();
            var result = runtime.provider().recognize(path, String.valueOf(attachment.get("mimeType")), runtime.endpoint(),
                    runtime.accessKeyId(), runtime.apiKey(), request.getDocumentType());
            if (result == null || result.rawResult() == null || result.normalizedResult() == null || !result.normalizedResult().isObject()) {
                throw new IllegalStateException("Invalid OCR response");
            }
            String rawJson = json.writeValueAsString(result.rawResult());
            if (rawJson.length() > 1048576) throw new IllegalStateException("Response too large");
            // 精确替换意外回显的凭据，SDK 错误/异常消息永不写入任务或操作日志。
            raw = redact(rawJson, runtime);
            ObjectNode suggestions = json.createObjectNode();
            for (String field : FIELDS) {
                JsonNode value = result.normalizedResult().get(field);
                if (value == null || value.isNull()) continue;
                if (!value.isValueNode() || value.asText().length() > (field.equals("text") ? 10000 : 500)) {
                    throw new IllegalStateException("Invalid normalized OCR field");
                }
                suggestions.put(field, redact(value.asText(), runtime));
            }
            suggestions.put("documentType", request.getDocumentType()); suggestions.put("sourceVersion", request.getVersion());
            normalized = json.writeValueAsString(suggestions); status = "SUCCEEDED"; error = null;
        } catch (Exception ex) {
            // 云服务异常可能包含 AK、签名或原票据内容，返回固定提示且保留原图。
            raw = null; normalized = null;
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
        }
        if (tasks.finish(id, provider, status, raw, normalized, error) != 1) throw new BusinessException(409, "OCR 任务状态已变化");
        return new IdResponse(id);
    }

    @DataScope
    public OcrTaskVO get(Long id) {
        requirePermission("bookkeeping:attachment:view");
        var task = requireTask(id); requireRecord(number(task,"recordId"), false);
        // 软删原附件后，不允许绕过附件查看接口继续读取识别内容。
        requireAttachment(number(task,"attachmentId"), number(task,"recordId"));
        return taskView(task);
    }

    @DataScope
    public PageResult<OcrTaskVO> list(Long recordId, PageQuery query) {
        requirePermission("bookkeeping:attachment:view"); requireRecord(recordId,false);
        var page = query == null ? new PageQuery(null,null,null,null) : query;
        int size = page.normalizedPageSize();
        return PageResult.of(tasks.selectTasks(recordId,(page.normalizedPageNum()-1)*size,size).stream().map(this::taskView).toList(),
                tasks.countTasks(recordId),page.normalizedPageNum(),size);
    }

    private OcrTaskVO taskView(Map<String,Object> task) {
        try {
            String normalized = text(task,"normalizedResult");
            return new OcrTaskVO(number(task,"id"),number(task,"recordId"),number(task,"attachmentId"),text(task,"provider"),text(task,"status"),
                    normalized == null ? null : json.readTree(normalized),text(task,"errorMessage"),number(task,"requestedBy"),
                    text(task,"startedAt"),text(task,"finishedAt"),text(task,"createTime"));
        } catch (Exception ex) { throw new BusinessException("OCR 结果无法读取"); }
    }

    @DataScope
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void confirm(Long id, RecordWriteRequest request) {
        requirePermission("bookkeeping:attachment:view"); requirePermission("bookkeeping:record:edit");
        if (request == null || request.getVersion() == null || request.getVersion() < 0) throw new BusinessException("缺少账目版本号");
        var task = requireTask(id); Long recordId = number(task,"recordId");
        var record = requireRecord(recordId, true); requireVersion(record, request.getVersion());
        requireAttachment(number(task,"attachmentId"), recordId);
        if (!"SUCCEEDED".equals(tasks.lockStatus(id))) throw new BusinessException(409, "仅成功且未确认的 OCR 任务可以确认");
        service.confirmOcr(recordId, request, id);
        if (tasks.confirm(id) != 1) throw new BusinessException(409, "OCR 任务已被确认，请刷新后重试");
    }

    private Map<String,Object> requireRecord(Long id, boolean editable) {
        if (id == null || id < 1) throw new BusinessException("账目 ID 不正确");
        var row = editable ? records.lockRecordForWrite(id, DataScopeContext.current()) : records.selectRecordForWrite(id, DataScopeContext.current());
        if (row == null) throw new BusinessException("账目不存在或无权访问");
        if (editable && !EDITABLE.contains(String.valueOf(row.get("status")))) throw new BusinessException(409, "当前账目状态不允许 OCR 录入");
        return row;
    }
    private Map<String,Object> requireAttachment(Long id, Long recordId) {
        var row = attachments.selectAttachment(id);
        if (row == null || !Objects.equals(number(row,"recordId"),recordId)) throw new BusinessException("原票据不存在或不属于该账目");
        return row;
    }
    private Map<String,Object> requireTask(Long id) {
        if (id == null || id < 1) throw new BusinessException("OCR 任务 ID 不正确");
        var row = tasks.selectTask(id); if (row == null) throw new BusinessException("OCR 任务不存在或无权访问"); return row;
    }
    private void requireVersion(Map<String,Object> row, int version) {
        if (((Number)row.get("version")).intValue() != version) throw new BusinessException(409, "账目已变化，请刷新后重试");
    }
    private void requirePermission(String permission) {
        if (!permissions.hasPermission(username(),permission)) throw new BusinessException(403,"无权执行该操作");
    }
    private String redact(String value, OcrConfigService.RuntimeConfig runtime) {
        value = value.replace(runtime.apiKey(), "[REDACTED]");
        if (!runtime.accessKeyId().isEmpty()) value = value.replace(runtime.accessKeyId(), "[REDACTED]");
        return value;
    }
    private static Long number(Map<String,Object> row, String key) { return ((Number)row.get(key)).longValue(); }
    private static String text(Map<String,Object> row, String key) { return row.get(key) == null ? null : String.valueOf(row.get(key)); }
    private String username() { var auth = SecurityContextHolder.getContext().getAuthentication(); return auth == null ? "" : auth.getName(); }
}
