package com.scaffold.bookkeeping.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 账目草稿写入参数。occurredDate / occurredTime 是实际发生时间，不使用创建时间替代。 */
public class RecordWriteRequest {

    @NotBlank(message = "账目类型不能为空")
    @Pattern(regexp = "INCOME|EXPENSE", message = "账目类型只能为收入或支出")
    private String recordType;
    @NotBlank(message = "账目摘要不能为空")
    @Size(max = 500, message = "账目摘要不能超过500个字符")
    private String summary;
    @NotNull(message = "实际发生日期不能为空")
    private LocalDate occurredDate;
    private LocalTime occurredTime;
    @NotNull(message = "账目总金额不能为空")
    @DecimalMin(value = "0.01", message = "账目总金额必须大于0")
    @Digits(integer = 16, fraction = 2, message = "金额最多16位整数、2位小数")
    private BigDecimal totalAmount;
    @NotNull(message = "支付方式不能为空") @Positive(message = "支付方式不正确")
    private Long paymentMethodId;
    @NotNull(message = "资金账户不能为空") @Positive(message = "资金账户不正确")
    private Long fundAccountId;
    @Positive(message = "所属部门不正确")
    private Long deptId;
    @Size(max = 1000, message = "备注不能超过1000个字符")
    private String remark;
    private Integer version;
    @NotEmpty(message = "至少需要一条账目明细")
    @Size(max = 100, message = "账目明细不能超过100条")
    @Valid
    private List<@NotNull(message = "明细不能为空") RecordDetailWriteRequest> details;

    public String getRecordType() { return recordType; }
    public void setRecordType(String recordType) { this.recordType = recordType; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public LocalDate getOccurredDate() { return occurredDate; }
    public void setOccurredDate(LocalDate occurredDate) { this.occurredDate = occurredDate; }
    public LocalTime getOccurredTime() { return occurredTime; }
    public void setOccurredTime(LocalTime occurredTime) { this.occurredTime = occurredTime; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public Long getPaymentMethodId() { return paymentMethodId; }
    public void setPaymentMethodId(Long paymentMethodId) { this.paymentMethodId = paymentMethodId; }
    public Long getFundAccountId() { return fundAccountId; }
    public void setFundAccountId(Long fundAccountId) { this.fundAccountId = fundAccountId; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public List<RecordDetailWriteRequest> getDetails() { return details; }
    public void setDetails(List<RecordDetailWriteRequest> details) { this.details = details; }
}
