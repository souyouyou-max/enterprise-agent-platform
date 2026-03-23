package com.enterprise.agent.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 多模态语义分析结果实体（对应 ocr_file_analysis 表）
 *
 * <p>对 {@link OcrFileMain} 中每个文件，调用正言大模型对分片图片进行结构化内容提取，
 * 结果落库于本表，供后续相似度对比、审核线索生成等流程使用。
 *
 * <h3>支持的文档类型（doc_type）</h3>
 * <ul>
 *   <li>BUSINESS_LICENSE — 营业执照</li>
 *   <li>QUOTATION       — 报价单</li>
 *   <li>CONTRACT        — 合同</li>
 *   <li>INVOICE         — 发票</li>
 *   <li>SEAL_PAGE       — 印章页（含章但无其他主体内容）</li>
 *   <li>ID_CARD         — 居民身份证</li>
 *   <li>WORK_SAFETY_LICENSE — 安全生产许可证等施工安全类证件</li>
 *   <li>CONSTRUCTION_QUALIFICATION — 建筑业企业资质证书等</li>
 *   <li>OTHER           — 其他</li>
 * </ul>
 * <p>证件专有字段（资质列表、身份证姓名号码等）落在 {@link #structuredExtra} JSON 中。
 */
@Data
@TableName("ocr_file_analysis")
public class OcrFileAnalysis {

    /** 状态常量 */
    public static final String STATUS_PENDING    = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SUCCESS    = "SUCCESS";
    public static final String STATUS_FAILED     = "FAILED";
    /** 文件无图片内容（如纯文本 .txt）时跳过分析 */
    public static final String STATUS_SKIPPED    = "SKIPPED";

    /** 文档类型常量 */
    public static final String DOC_TYPE_BUSINESS_LICENSE = "BUSINESS_LICENSE";
    public static final String DOC_TYPE_QUOTATION        = "QUOTATION";
    public static final String DOC_TYPE_CONTRACT         = "CONTRACT";
    public static final String DOC_TYPE_INVOICE          = "INVOICE";
    public static final String DOC_TYPE_SEAL_PAGE        = "SEAL_PAGE";
    public static final String DOC_TYPE_ID_CARD          = "ID_CARD";
    public static final String DOC_TYPE_WORK_SAFETY_LICENSE = "WORK_SAFETY_LICENSE";
    public static final String DOC_TYPE_CONSTRUCTION_QUALIFICATION = "CONSTRUCTION_QUALIFICATION";
    public static final String DOC_TYPE_OTHER            = "OTHER";

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属主文件 ID，关联 ocr_file_main.id */
    @TableField("main_id")
    private Long mainId;

    /** 所属批次流水号（冗余，便于按批次查询） */
    @TableField("batch_no")
    private String batchNo;

    // ── 结构化提取字段 ────────────────────────────────────────────

    /**
     * 文档类型：BUSINESS_LICENSE / QUOTATION / CONTRACT / INVOICE / SEAL_PAGE / OTHER
     */
    @TableField("doc_type")
    private String docType;

    /** 是否有公章（0=无，1=有） */
    @TableField("has_stamp")
    private Boolean hasStamp;

    /** 公章文字内容（如"××有限公司""合同专用章"等） */
    @TableField("stamp_text")
    private String stampText;

    /** 公司/单位名称 */
    @TableField("company_name")
    private String companyName;

    /** 营业执照统一社会信用代码（18位） */
    @TableField("license_no")
    private String licenseNo;

    /** 报价/合同总金额（元），无则 NULL */
    @TableField("total_amount")
    private BigDecimal totalAmount;

    /** 关键日期（逗号分隔，如"2026-03-01,2026-06-30"） */
    @TableField("key_dates")
    private String keyDates;

    /** 文档摘要（100字以内，由大模型生成） */
    @TableField("doc_summary")
    private String docSummary;

    /**
     * 扩展结构化字段 JSON：模型返回中未映射到固定列的键（如身份证姓名/号码、资质类别及等级、证书编号等）。
     */
    @TableField("structured_extra")
    private String structuredExtra;

    // ── 分析元数据 ────────────────────────────────────────────────

    /** 分析时使用的提示词 */
    @TableField("analysis_prompt")
    private String analysisPrompt;

    /** 正言大模型原始响应 JSON（用于问题排查与重处理） */
    @TableField("analysis_raw")
    private String analysisRaw;

    /** 分析状态：PENDING / PROCESSING / SUCCESS / FAILED / SKIPPED */
    @TableField("status")
    private String status;

    /** 失败原因 */
    @TableField("error_message")
    private String errorMessage;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * {@link #docType} 枚举值对应的中文展示名（与前端 DOC_TYPE_LABELS 一致）。
     * 未知枚举时返回原码值。
     */
    public static String labelOfDocType(String docType) {
        if (docType == null || docType.isBlank()) {
            return "未知";
        }
        return switch (docType) {
            case DOC_TYPE_BUSINESS_LICENSE -> "营业执照";
            case DOC_TYPE_WORK_SAFETY_LICENSE -> "安全生产许可证";
            case DOC_TYPE_CONSTRUCTION_QUALIFICATION -> "建筑业资质证书";
            case DOC_TYPE_ID_CARD -> "身份证";
            case DOC_TYPE_QUOTATION -> "报价单";
            case DOC_TYPE_CONTRACT -> "合同";
            case DOC_TYPE_INVOICE -> "发票";
            case DOC_TYPE_SEAL_PAGE -> "印章页";
            case DOC_TYPE_OTHER -> "其他";
            default -> docType;
        };
    }
}
