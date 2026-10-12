package com.scaffold.bookkeeping.domain.vo;

/** encryptionReady 加密环境是否就绪；keyConfigured 是否存有密钥；keySuffix 脱敏后缀；recognitionAvailable 能否实际识别。 */
public record OcrConfigStatusVO(String provider, String endpoint, boolean encryptionReady,
                                boolean keyConfigured, String keySuffix, String accessKeyIdSuffix, boolean recognitionAvailable) {}
