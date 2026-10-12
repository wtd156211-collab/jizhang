package com.scaffold.bookkeeping.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** version 当前账目版本号；reason 退回、撤回、作废原因。 */
public class ReviewActionRequest {
    @NotNull(message = "缺少账目版本号，请刷新后重试")
    @Min(value = 0, message = "账目版本号不正确")
    private Integer version;
    @Size(max = 1000, message = "原因不能超过1000个字符")
    private String reason;

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
