package com.scaffold.bookkeeping.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 账目明细写入参数：categoryId 分类，amount 金额，handlerId / deptId / projectId 关联对象。 */
public class RecordDetailWriteRequest {

    @NotNull(message = "明细分类不能为空")
    @Positive(message = "明细分类不正确")
    private Long categoryId;
    @NotNull(message = "明细金额不能为空")
    @DecimalMin(value = "0.01", message = "明细金额必须大于0")
    @Digits(integer = 16, fraction = 2, message = "金额最多16位整数、2位小数")
    private BigDecimal amount;
    @Positive(message = "经办人不正确")
    private Long handlerId;
    @Positive(message = "部门不正确")
    private Long deptId;
    @Positive(message = "项目不正确")
    private Long projectId;
    @Size(max = 500, message = "明细备注不能超过500个字符")
    private String remark;
    @Min(value = 0, message = "排序号不能小于0")
    @Max(value = 9999, message = "排序号不能大于9999")
    private Integer sort;

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Long getHandlerId() { return handlerId; }
    public void setHandlerId(Long handlerId) { this.handlerId = handlerId; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
}
