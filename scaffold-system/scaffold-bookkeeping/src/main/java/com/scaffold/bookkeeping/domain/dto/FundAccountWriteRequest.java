package com.scaffold.bookkeeping.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 资金账户写入参数。字段含义：accountCode 编码，accountType 账户类型。 */
public class FundAccountWriteRequest {

    @NotBlank(message = "资金账户编码不能为空")
    @Size(max = 64, message = "资金账户编码不能超过64个字符")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "资金账户编码只能包含字母、数字、下划线或短横线")
    private String accountCode;
    @NotBlank(message = "资金账户名称不能为空")
    @Size(max = 100, message = "资金账户名称不能超过100个字符")
    private String accountName;
    @NotBlank(message = "账户类型不能为空")
    @Size(max = 32, message = "账户类型不能超过32个字符")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "账户类型只能包含字母、数字、下划线或短横线")
    private String accountType;
    @NotNull(message = "排序号不能为空")
    @Min(value = 0, message = "排序号不能小于0")
    @Max(value = 9999, message = "排序号不能大于9999")
    private Integer sort;
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能为0或1")
    @Max(value = 1, message = "状态只能为0或1")
    private Integer status;
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;

    public String getAccountCode() { return accountCode; }
    public void setAccountCode(String accountCode) { this.accountCode = accountCode; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
