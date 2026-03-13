package com.enterprise.agent.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 供应商工商信息实体（对应 supplier_info 表）
 * 数据来源：企查查/天眼查（QichachaAdapter 同步）
 */
@Data
@TableName("supplier_info")
public class SupplierInfo {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 供应商ID（唯一标识） */
    @TableField("supplier_id")
    private String supplierId;

    /** 供应商名称 */
    @TableField("supplier_name")
    private String supplierName;

    /** 法定代表人（用于围标/利益冲突检测） */
    @TableField("legal_person")
    private String legalPerson;

    /** 注册资本（万元） */
    @TableField("registered_capital")
    private BigDecimal registeredCapital;

    /** 股东信息 JSON 数组，格式：[{"name":"xx","ratio":30}] */
    @TableField("shareholders")
    private String shareholders;

    /** 经营范围 */
    @TableField("business_scope")
    private String businessScope;

    /** 风险等级：LOW/MEDIUM/HIGH */
    @TableField("risk_level")
    private String riskLevel;

    @TableField(value = "synced_at", fill = FieldFill.INSERT)
    private LocalDateTime syncedAt;
}
