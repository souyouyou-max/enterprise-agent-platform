package com.enterprise.agent.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 招标项目实体（对应 procurement_project 表）
 * 数据来源：招采系统（ZhaocaiSystemAdapter 同步）
 */
@Data
@TableName("procurement_project")
public class ProcurementProject {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 项目编码（唯一标识，对应费控系统 payment_record.contract_no） */
    @TableField("project_code")
    private String projectCode;

    /** 项目名称 */
    @TableField("project_name")
    private String projectName;

    /** 机构编码 */
    @TableField("org_code")
    private String orgCode;

    /** 合同金额（元） */
    @TableField("contract_amount")
    private BigDecimal contractAmount;

    /** 招标方式：公开招标/竞争性谈判/单一来源/直接采购 */
    @TableField("bid_method")
    private String bidMethod;

    /** 中标供应商名称 */
    @TableField("supplier_name")
    private String supplierName;

    /** 中标供应商ID */
    @TableField("supplier_id")
    private String supplierId;

    /** 项目日期（招标完成日期） */
    @TableField("project_date")
    private LocalDate projectDate;

    /** 是否有完整招标流程 */
    @TableField("has_bid_process")
    private Boolean hasBidProcess;

    @TableField(value = "synced_at", fill = FieldFill.INSERT)
    private LocalDateTime syncedAt;
}
