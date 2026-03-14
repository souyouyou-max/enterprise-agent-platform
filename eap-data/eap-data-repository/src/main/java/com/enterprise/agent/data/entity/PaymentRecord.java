package com.enterprise.agent.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 付款台账实体（对应 payment_record 表）
 * 数据来源：费控系统（FeikongSystemAdapter 同步）
 */
@Data
@TableName("payment_record")
public class PaymentRecord {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 合同编号（关联招采系统 procurement_project.project_code，用于判断是否有招标） */
    @TableField("contract_no")
    private String contractNo;

    /** 机构编码 */
    @TableField("org_code")
    private String orgCode;

    /** 供应商名称 */
    @TableField("supplier_name")
    private String supplierName;

    /** 供应商ID */
    @TableField("supplier_id")
    private String supplierId;

    /** 付款金额（元） */
    @TableField("payment_amount")
    private BigDecimal paymentAmount;

    /** 付款日期 */
    @TableField("payment_date")
    private LocalDate paymentDate;

    /** 项目类别（如：IT服务/软件开发/办公用品/工程建设） */
    @TableField("project_category")
    private String projectCategory;

    /** 付款用途说明 */
    @TableField("payment_purpose")
    private String paymentPurpose;

    @TableField(value = "synced_at", fill = FieldFill.INSERT)
    private LocalDateTime syncedAt;
}
