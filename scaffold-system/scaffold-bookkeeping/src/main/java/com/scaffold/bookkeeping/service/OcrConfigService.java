package com.scaffold.bookkeeping.service;

import com.scaffold.bookkeeping.domain.dto.OcrConfigRequest;
import com.scaffold.bookkeeping.domain.vo.OcrConfigStatusVO;
import com.scaffold.bookkeeping.mapper.OcrConfigMapper;
import com.scaffold.bookkeeping.ocr.OcrProvider;
import com.scaffold.bookkeeping.ocr.AliyunOcrProvider;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.system.mapper.AuthMapper;
import com.scaffold.system.service.SecretConfigService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OcrConfigService {
    private static final String PREFIX = "bookkeeping.ocr.";
    private final OcrConfigMapper mapper;
    private final SecretConfigService secrets;
    private final AuthMapper auth;
    private final Map<String, OcrProvider> providers;
    public OcrConfigService(OcrConfigMapper mapper, SecretConfigService secrets, AuthMapper auth, List<OcrProvider> providers) {
        this.mapper = mapper; this.secrets = secrets; this.auth = auth;
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(OcrProvider::id, Function.identity()));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public OcrConfigStatusVO status() {
        requireAdmin();
        var settings = settings();
        String provider = settings.getOrDefault(PREFIX + "provider", "disabled");
        String encrypted = settings.getOrDefault(PREFIX + "api_key", "");
        boolean configured = !encrypted.isBlank();
        String suffix = "";
        boolean readable = false;
        if (configured) {
            suffix = "******";
            if (secrets.ready()) {
                try {
                    String plain = secrets.decrypt(encrypted);
                    suffix = plain.length() > 4 ? "******" + plain.substring(plain.length() - 4) : "******";
                    readable = true;
                } catch (BusinessException ignored) { /* 密钥不匹配仍允许管理员替换，绝不回显密文。 */ }
            }
        }
        String idSuffix = "";
        if ("aliyun".equals(provider)) {
            String encryptedId = settings.getOrDefault(PREFIX + "access_key_id", "");
            if (!encryptedId.isBlank()) {
                idSuffix = "******";
                try {
                    String id = secrets.decrypt(encryptedId);
                    if (id.length() > 4) idSuffix += id.substring(id.length() - 4);
                } catch (BusinessException ignored) { readable = false; }
            } else readable = false;
        }
        return new OcrConfigStatusVO(provider, settings.getOrDefault(PREFIX + "endpoint", ""), secrets.ready(), configured, suffix, idSuffix,
                readable && providers.containsKey(provider) && providers.get(provider).available());
    }

    @Transactional
    public void save(OcrConfigRequest request) {
        requireAdmin();
        if (request == null || request.getProvider() == null || !providers.containsKey(request.getProvider())) {
            throw new BusinessException("OCR 服务商未接入");
        }
        String endpoint = request.getEndpoint() == null ? "" : request.getEndpoint().trim();
        validateEndpoint(endpoint);
        if ("aliyun".equals(request.getProvider()) && !endpoint.isEmpty() && !AliyunOcrProvider.ENDPOINT.equals(endpoint)) {
            throw new BusinessException("阿里云 OCR 地址固定为杭州官方 HTTPS 地址");
        }
        if (request.isClearApiKey() && ((request.getApiKey() != null && !request.getApiKey().isEmpty())
                || (request.getAccessKeyId() != null && !request.getAccessKeyId().isEmpty()))) {
            throw new BusinessException("不能同时替换和清空 API Key");
        }
        String encrypted = null;
        if (request.isClearApiKey()) encrypted = "";
        else if (request.getApiKey() != null && !request.getApiKey().isEmpty()) encrypted = secrets.encrypt(request.getApiKey());
        String encryptedId = null;
        if (request.isClearApiKey()) encryptedId = "";
        else if (request.getAccessKeyId() != null && !request.getAccessKeyId().isEmpty()) encryptedId = secrets.encrypt(request.getAccessKeyId());
        mapper.upsert(PREFIX + "provider", request.getProvider(), username());
        mapper.upsert(PREFIX + "endpoint", endpoint, username());
        // 留空保留原值，只在有明确动作时更新，避免并发配置覆盖另一人的密钥。
        if (encrypted != null) mapper.upsert(PREFIX + "api_key", encrypted, username());
        if (encryptedId != null) mapper.upsert(PREFIX + "access_key_id", encryptedId, username());
    }

    /** 内部调用，绝不作为 Controller 返回值。单条查询取得一致凭据，缺配置不会标记外层任务事务回滚。 */
    public RuntimeConfig runtime() {
        var settings = settings();
        String name = settings.getOrDefault(PREFIX + "provider", "disabled");
        OcrProvider provider = providers.get(name);
        if (provider == null || !provider.available()) throw new BusinessException("尚未接入 OCR 服务商，可继续人工录入");
        String encrypted = settings.getOrDefault(PREFIX + "api_key", "");
        if (encrypted.isBlank()) throw new BusinessException("尚未配置 OCR API Key，可继续人工录入");
        String encryptedId = settings.getOrDefault(PREFIX + "access_key_id", "");
        if ("aliyun".equals(name) && encryptedId.isBlank()) throw new BusinessException("尚未配置阿里云 AccessKey ID");
        return new RuntimeConfig(provider, settings.getOrDefault(PREFIX + "endpoint", ""), encryptedId.isBlank() ? "" : secrets.decrypt(encryptedId), secrets.decrypt(encrypted));
    }

    public String providerName() { String value = value("provider"); return value.isBlank() ? "disabled" : value; }
    private String value(String key) { String value = mapper.selectValue(PREFIX + key); return value == null ? "" : value; }
    private Map<String,String> settings() {
        return mapper.selectSettings().stream().collect(Collectors.toMap(row -> row.get("name"), row -> row.get("value")));
    }
    private void validateEndpoint(String endpoint) {
        if (endpoint.isEmpty()) return;
        try {
            URI uri = URI.create(endpoint);
            if (endpoint.length() > 256 || !"https".equals(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) { throw new BusinessException("OCR 地址须为不含凭据、查询参数或片段的 HTTPS 地址"); }
    }
    private void requireAdmin() {
        var user = auth.selectUserByUsername(username());
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())
                || auth.selectRoleCodes(user.getId()).stream().noneMatch(Set.of("super_admin", "sys_admin")::contains)) {
            throw new BusinessException(403, "只有系统管理员可以管理 OCR 配置");
        }
    }
    private String username() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "" : authentication.getName();
    }
    public static final class RuntimeConfig {
        private final OcrProvider provider;
        private final String endpoint;
        private final String apiKey;
        private final String accessKeyId;
        RuntimeConfig(OcrProvider provider, String endpoint, String accessKeyId, String apiKey) { this.provider = provider; this.endpoint = endpoint; this.accessKeyId = accessKeyId; this.apiKey = apiKey; }
        public OcrProvider provider() { return provider; }
        public String endpoint() { return endpoint; }
        public String apiKey() { return apiKey; }
        public String accessKeyId() { return accessKeyId; }
        @Override public String toString() { return "OCR runtime configuration [redacted]"; }
    }
}
