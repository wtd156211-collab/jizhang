package com.scaffold.bookkeeping.domain.dto;

import jakarta.validation.constraints.*;

/** attachmentId 已保存的原票据；version 当前账目版本；documentType 识别类型。 */
public class OcrStartRequest {
    @NotNull @Positive private Long attachmentId;
    @NotNull @Min(0) private Integer version;
    @NotBlank @Pattern(regexp = "Invoice|ShoppingReceipt|PaymentRecord|Advanced")
    private String documentType = "Invoice";
    public Long getAttachmentId() { return attachmentId; }
    public void setAttachmentId(Long attachmentId) { this.attachmentId = attachmentId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String documentType) { this.documentType = documentType; }
}
