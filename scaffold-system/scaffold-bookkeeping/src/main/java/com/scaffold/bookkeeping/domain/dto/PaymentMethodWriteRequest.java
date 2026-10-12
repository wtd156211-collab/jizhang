package com.scaffold.bookkeeping.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 支付方式写入参数。字段含义：methodCode 编码，methodName 名称。 */
public class PaymentMethodWriteRequest {

    @NotBlank(message = "支付方式编码不能为空")
    @Size(max = 64, message = "支付方式编码不能超过64个字符")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "支付方式编码只能包含字母、数字、下划线或短横线")
    private String methodCode;
    @NotBlank(message = "支付方式名称不能为空")
    @Size(max = 100, message = "支付方式名称不能超过100个字符")
    private String methodName;
    @NotNull(message = "排序号不能为空")
    @Min(value = 0, message = "排序号不能小于0")
    @Max(value = 9999, message = "排序号不能大于9999")
    private Integer sort;
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能为0或1")
    @Max(value = 1, message = "状态只能为0或1")
    private Integer status;

    public String getMethodCode() { return methodCode; }
    public void setMethodCode(String methodCode) { this.methodCode = methodCode; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
