package com.enterprise.agent.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购合同实体（对应 procurement_contract 表）
 */
@Data
@TableName("procurement_contract")
public class ProcurementContract {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 机构编码 */
    @TableField("org_code")
    private String orgCode;

    /** 项目名称 */
    @TableField("project_name")
    private String projectName;

    /** 供应商名称 */
    @TableField("supplier_name")
    private String supplierName;

    /** 供应商ID */
    @TableField("supplier_id")
    private String supplierId;

    /** 合同金额（元） */
    @TableField("contract_amount")
    private BigDecimal contractAmount;

    /** 实际付款金额（元） */
    @TableField("payment_amount")
    private BigDecimal paymentAmount;

    /** 合同签署日期 */
    @TableField("contract_date")
    private LocalDate contractDate;

    /** 付款日期 */
    @TableField("payment_date")
    private LocalDate paymentDate;

    /** 是否有招采流程（true=有，false=无） */
    @TableField("has_zc_process")
    private Boolean hasZcProcess;

    /** 项目类别（如：IT服务/软件开发/办公用品/工程建设） */
    @TableField("project_category")
    private String projectCategory;

    /** 逻辑删除（0=正常，1=删除） */
    @TableField("deleted")
    @TableLogic
    private Integer deleted;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
