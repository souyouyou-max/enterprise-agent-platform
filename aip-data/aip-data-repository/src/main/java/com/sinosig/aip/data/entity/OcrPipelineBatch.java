package com.sinosig.aip.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OCR 流水线批次实体（对应 ocr_pipeline_batch 表）
 *
 * <p>一次"多文件上传"对应一条批次记录，通过 {@link #batchNo} 将多个 {@link OcrFileMain} 聚合在一起。
 *
 * <h3>批次状态机</h3>
 * <pre>
 *                          ┌─────────────────────────────────────────┐
 *                          │                                         ▼
 * PENDING → OCR_PROCESSING → OCR_DONE → ANALYZING → ANALYZED → COMPARING → DONE
 *              │                │            │            │           │
 *              └── PARTIAL_FAIL ┘            └── FAILED ──┘           └── FAILED
 *
 * PARTIAL_FAIL：部分文件 OCR 失败，但剩余文件足以继续后续阶段（由配置决定是否继续）
 * FAILED      ：严重失败（如所有文件均失败，或系统异常）
 * </pre>
 */
@Data
@TableName("ocr_pipeline_batch")
public class OcrPipelineBatch {

    /** 状态常量 */
    public static final String STATUS_PENDING          = "PENDING";
    public static final String STATUS_OCR_PROCESSING   = "OCR_PROCESSING";
    public static final String STATUS_OCR_DONE         = "OCR_DONE";
    public static final String STATUS_ANALYZING        = "ANALYZING";
    public static final String STATUS_ANALYZED         = "ANALYZED";
    public static final String STATUS_COMPARING        = "COMPARING";
    public static final String STATUS_DONE             = "DONE";
    public static final String STATUS_PARTIAL_FAIL     = "PARTIAL_FAIL";
    public static final String STATUS_FAILED           = "FAILED";

    /** 触发来源常量 */
    public static final String SOURCE_API       = "API";
    public static final String SOURCE_SCHEDULER = "SCHEDULER";

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 批次流水号，全局唯一，由调用方传入或由服务层自动生成 */
    @TableField("batch_no")
    private String batchNo;

    /** 应用/租户编码 */
    @TableField("app_code")
    private String appCode;

    /** 触发来源：API / SCHEDULER */
    @TableField("trigger_source")
    private String triggerSource;

    /** 批次文件总数 */
    @TableField("total_files")
    private Integer totalFiles;

    /** 已完成 OCR 的文件数 */
    @TableField("ocr_done_files")
    private Integer ocrDoneFiles;

    /** 已完成语义分析的文件数 */
    @TableField("analysis_done_files")
    private Integer analysisDoneFiles;

    /** 批次整体状态 */
    @TableField("status")
    private String status;

    /** 最近一次失败原因 */
    @TableField("error_message")
    private String errorMessage;

    /** 扩展 JSON（调用方透传字段，如业务单号、申请人等） */
    @TableField("extra_info")
    private String extraInfo;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
