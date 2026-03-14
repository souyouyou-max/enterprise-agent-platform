package com.enterprise.agent.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 供应商关联关系实体（对应 supplier_relation 表）
 * 存储供应商股东/法人/董监高信息，用于利益输送检测
 */
@Data
@TableName("supplier_relation")
public class SupplierRelation {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 供应商ID */
    @TableField("supplier_id")
    private String supplierId;

    /** 供应商名称 */
    @TableField("supplier_name")
    private String supplierName;

    /** 关联人员姓名（股东/法人/董监高） */
    @TableField("related_person_name")
    private String relatedPersonName;

    /** 关联类型（股东/法人/董事/监事/高管） */
    @TableField("relation_type")
    private String relationType;

    /** 持股比例（仅股东有效，百分比） */
    @TableField("share_ratio")
    private BigDecimal shareRatio;

    /** 关联的内部员工ID（为空表示尚未发现关联） */
    @TableField("internal_employee_id")
    private String internalEmployeeId;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
