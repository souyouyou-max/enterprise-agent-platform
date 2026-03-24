package com.sinosig.aip.business.pipeline.event;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OCR Pipeline 批次状态流转领域事件（用于结构化日志与后续扩展）
 * <p>
 * 字段约定：
 * <ul>
 *   <li>{@code pipelineType} 区分多套 Pipeline</li>
 *   <li>{@code taskId} 对应批次号 batchNo（语义上可与通用任务 ID 对齐）</li>
 *   <li>{@code previousStatus} / {@code currentStatus} 记录状态流转</li>
 * </ul>
 */
@Data
@Builder
public class OcrPipelineEvent {

    /** Pipeline 类型标识，固定值 "OCR"，便于多 Pipeline 统一监控 */
    @Builder.Default
    private String pipelineType = "OCR";

    /** 批次号（对应 ocr_pipeline_batch.batch_no） */
    private String taskId;

    /** 应用/租户编码 */
    private String appCode;

    /** 前一状态 */
    private String previousStatus;

    /** 当前状态 */
    private String currentStatus;

    /**
     * 事件类型：
     * SUBMITTED    - 批次提交成功
     * STATUS_CHANGED - 阶段状态流转
     * COMPLETED    - 批次全流程完成（DONE）
     * PARTIAL_FAIL - 部分文件失败，流程继续
     * FAILED       - 批次失败
     */
    private String eventType;

    /** 附加消息（如失败原因、完成文件数等） */
    private String message;

    /** 批次总文件数 */
    private Integer totalFiles;

    /** 事件发生时间 */
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
}
