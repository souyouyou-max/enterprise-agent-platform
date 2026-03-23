package com.enterprise.agent.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OCR主文件记录实体（对应 ocr_file_main 表）
 * <p>
 * 记录大智部OCR和正言多模态识别的文件基本信息，
 * 每条记录对应一个待识别/已识别的原始文件。
 * 拆分页/子文件存储在 ocr_file_split 表中。
 */
@Data
@TableName("ocr_file_main")
public class OcrFileMain {

    /**
     * OceanBase（Oracle兼容）下 AUTO + 返回主键可能触发 JDBC RETURNING 参数位异常。
     * 使用 MP 雪花算法生成ID，避免依赖数据库返回自增主键。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 业务编号（唯一）：关联业务单据，如采购单号、申请单号等
     */
    @TableField("business_no")
    private String businessNo;

    /**
     * 批次流水号：同一次多文件上传共用同一 batch_no，关联 ocr_pipeline_batch.batch_no。
     * 单文件独立请求时可为 NULL。
     */
    @TableField("batch_no")
    private String batchNo;

    /**
     * OCR来源类型：DAZHI_OCR（大智部）/ ZHENGYAN_MULTIMODAL（正言多模态）
     */
    @TableField("source")
    private String source;

    /**
     * 原始文件名
     */
    @TableField("file_name")
    private String fileName;

    /**
     * 文件类型：pdf / jpg / png / tiff 等
     */
    @TableField("file_type")
    private String fileType;

    /**
     * 文件大小（字节）
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 文件完整存储路径（含 bucket），格式：bucket/path/to/file.pdf
     * 不含协议前缀，由业务层拼接后传入
     */
    @TableField("file_path")
    private String filePath;

    /**
     * OCR处理状态：PENDING / PROCESSING / SUCCESS / FAILED
     */
    @TableField("ocr_status")
    private String ocrStatus;

    /**
     * 多模态语义分析状态：PENDING / PROCESSING / SUCCESS / FAILED / SKIPPED。
     * 在 OCR 完成后由流水线触发正言大模型分析时更新。
     */
    @TableField("analysis_status")
    private String analysisStatus;

    /**
     * 总页数/总分片数（拆分后写入）
     */
    @TableField("total_pages")
    private Integer totalPages;

    /**
     * 发给识别引擎的提示词（原始 prompt，用于问题排查和效果回溯）
     */
    @TableField("prompt")
    private String prompt;

    /**
     * 识别结果文本：
     * - 无拆分（单文件）时：识别完成后直接写入
     * - 有拆分时：所有分片完成后按 split_index 顺序聚合写入
     */
    @TableField("ocr_result")
    private String ocrResult;

    /**
     * 失败时的错误信息
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 应用编码（多租户或多系统区分）
     */
    @TableField("app_code")
    private String appCode;

    /**
     * 扩展信息（JSON格式，存储各provider返回的原始元数据）
     */
    @TableField("extra_info")
    private String extraInfo;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
