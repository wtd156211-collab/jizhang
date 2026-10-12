package com.scaffold.bookkeeping.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scaffold.bookkeeping.domain.vo.RecordVersionVO;
import com.scaffold.bookkeeping.domain.vo.ReviewHistoryVO;
import com.scaffold.bookkeeping.mapper.AttachmentMapper;
import com.scaffold.bookkeeping.mapper.RecordMapper;
import com.scaffold.bookkeeping.mapper.ReviewMapper;
import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.framework.datascope.DataScope;
import com.scaffold.framework.datascope.DataScopeContext;
import com.scaffold.framework.security.PermissionResolver;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/** JSON 快照与主表变更必须共用事务。读取按账目范围授权，附件信息额外检查附件查看权限。 */
@Service
public class RecordHistoryService {
    private final RecordMapper records;
    private final AttachmentMapper attachments;
    private final ReviewMapper reviews;
    private final ObjectMapper json;
    private final PermissionResolver permissions;

    public RecordHistoryService(RecordMapper records, AttachmentMapper attachments, ReviewMapper reviews,
                                ObjectMapper json, PermissionResolver permissions) {
        this.records = records;
        this.attachments = attachments;
        this.reviews = reviews;
        this.json = json;
        this.permissions = permissions;
    }

    public JsonNode capture(Map<String, Object> row) {
        long id = ((Number) row.get("id")).longValue();
        Map<String, Object> value = new LinkedHashMap<>(row);
        value.put("details", records.selectDetails(id));
        value.put("attachments", attachments.selectAttachments(id).stream().map(attachment -> {
            Map<String, Object> metadata = new LinkedHashMap<>(attachment);
            metadata.remove("storageKey");
            return metadata;
        }).toList());
        return json.valueToTree(value);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void append(Long id, int version, String action, String reason, Long operatorId, JsonNode before) {
        ObjectNode snapshot = json.createObjectNode();
        snapshot.set("before", before);
        snapshot.set("after", capture(requireVisible(id)));
        try {
            if (reviews.insertVersion(id, version, action, json.writeValueAsString(snapshot), reason, operatorId) != 1) {
                throw new BusinessException("账目历史保存失败，请重试");
            }
        } catch (JsonProcessingException ex) {
            throw new BusinessException("账目快照生成失败，请重试");
        }
    }

    @DataScope
    @Transactional(readOnly = true)
    public PageResult<ReviewHistoryVO> reviews(Long id, PageQuery query) {
        requireVisible(id);
        PageQuery page = query == null ? new PageQuery(null, null, null, null) : query;
        int size = page.normalizedPageSize();
        int offset = (page.normalizedPageNum() - 1) * size;
        return PageResult.of(reviews.selectReviews(id, offset, size), reviews.countReviews(id), page.normalizedPageNum(), size);
    }

    @DataScope
    @Transactional(readOnly = true)
    public PageResult<RecordVersionVO> versions(Long id, PageQuery query) {
        requireVisible(id);
        PageQuery page = query == null ? new PageQuery(null, null, null, null) : query;
        int size = page.normalizedPageSize();
        int offset = (page.normalizedPageNum() - 1) * size;
        boolean includeAttachments = permissions.hasPermission(username(), "bookkeeping:attachment:view");
        var rows = reviews.selectVersions(id, offset, size).stream().map(row -> version(row, includeAttachments)).toList();
        return PageResult.of(rows, reviews.countVersions(id), page.normalizedPageNum(), size);
    }

    private RecordVersionVO version(Map<String, Object> row, boolean includeAttachments) {
        try {
            JsonNode snapshot = json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .readTree(String.valueOf(row.get("snapshotJson")));
            JsonNode before = snapshot.get("before");
            JsonNode after = snapshot.get("after");
            if (!includeAttachments) {
                if (before instanceof ObjectNode object) object.remove("attachments");
                if (after instanceof ObjectNode object) object.remove("attachments");
            }
            return new RecordVersionVO(((Number) row.get("id")).longValue(), ((Number) row.get("recordId")).longValue(),
                    ((Number) row.get("versionNo")).intValue(), (String) row.get("action"), (String) row.get("reason"),
                    ((Number) row.get("operatorId")).longValue(), (String) row.get("operatorName"), (String) row.get("createTime"), before, after);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("账目历史读取失败，请联系管理员");
        }
    }

    private Map<String, Object> requireVisible(Long id) {
        if (id == null || id < 1) throw new BusinessException("账目 ID 不正确");
        Map<String, Object> row = records.selectRecordForHistory(id, DataScopeContext.current());
        if (row == null) throw new BusinessException("账目不存在或无权访问");
        return row;
    }

    private String username() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }
}
