package com.scaffold.bookkeeping.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** provider 服务标识；endpoint HTTPS 服务地址；apiKey 替换值；clearApiKey 显式清空。 */
public class OcrConfigRequest {
    @NotBlank @Size(max = 64)
    private String provider;
    @Size(max = 256)
    private String endpoint;
    @Size(max = 256)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String apiKey;
    @Size(max = 256)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String accessKeyId;
    private boolean clearApiKey;
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
    public boolean isClearApiKey() { return clearApiKey; }
    public void setClearApiKey(boolean clearApiKey) { this.clearApiKey = clearApiKey; }
}
