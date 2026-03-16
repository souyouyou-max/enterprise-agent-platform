package com.enterprise.agent.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 投标记录实体（对应 procurement_bid 表）
 */
@Data
@TableName("procurement_bid")
public class ProcurementBid {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 招标项目ID */
    @TableField("bid_project_id")
    private String bidProjectId;

    /** 招标项目名称 */
    @TableField("project_name")
    private String projectName;

    /** 投标供应商名称 */
    @TableField("supplier_name")
    private String supplierName;

    /** 投标供应商ID */
    @TableField("supplier_id")
    private String supplierId;

    /** 投标报价（元） */
    @TableField("bid_amount")
    private BigDecimal bidAmount;

    /** 投标文件摘要（关键词，用于相似度比对） */
    @TableField("bid_content")
    private String bidContent;

    /** 法定代表人 */
    @TableField("legal_person")
    private String legalPerson;

    /** 股东信息（JSON格式，如：["张三","李四"]） */
    @TableField("shareholders")
    private String shareholders;

    /** 是否中标 */
    @TableField("is_winner")
    private Boolean isWinner;

    /** 投标日期 */
    @TableField("bid_date")
    private LocalDate bidDate;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 招采系统文件下载URL */
    @TableField("bid_document_url")
    private String bidDocumentUrl;

    /** MinIO存储路径 */
    @TableField("bid_document_minio_pth")
    private String bidDocumentMinioPth;

    /** 解析后纯文本 */
    @TableField("bid_document_text")
    private String bidDocumentText;

    /** 报价明细JSON */
    @TableField("bid_price_detail")
    private String bidPriceDetail;
}
