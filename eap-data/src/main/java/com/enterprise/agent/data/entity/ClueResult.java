package com.enterprise.agent.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 疑点线索结果实体（对应 clue_result 表）
 * 由规则SQL引擎写入，Agent 读取后进行智能分析
 */
@Data
@TableName("clue_result")
public class ClueResult {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 机构编码 */
    @TableField("org_code")
    private String orgCode;

    /** 线索类型：UNTENDERED / SPLIT_PURCHASE / COLLUSIVE_BID / CONFLICT_OF_INTEREST */
    @TableField("clue_type")
    private String clueType;

    /** 风险等级：HIGH / MEDIUM / LOW */
    @TableField("risk_level")
    private String riskLevel;

    /** 线索标题（简要描述） */
    @TableField("clue_title")
    private String clueTitle;

    /** 线索详情（规则命中的具体数据描述） */
    @TableField("clue_detail")
    private String clueDetail;

    /** 涉及金额（元） */
    @TableField("related_amount")
    private BigDecimal relatedAmount;

    /** 涉及供应商名称 */
    @TableField("related_supplier")
    private String relatedSupplier;

    /** 命中的规则名称 */
    @TableField("rule_name")
    private String ruleName;

    /** 处理状态：PENDING / CONFIRMED / DISMISSED */
    @TableField("status")
    private String status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
