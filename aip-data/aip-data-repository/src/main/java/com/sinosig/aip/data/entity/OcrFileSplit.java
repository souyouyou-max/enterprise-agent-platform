package com.sinosig.aip.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * OCR拆分文件记录实体（对应 ocr_file_split 表）
 * <p>
 * 存储主文件按页/按区块拆分后每个子文件的识别结果。
 * 通过 main_id 关联 ocr_file_main 表。
 * 所有子文件识别完成后，由 OcrFileDataService 聚合写回主文件表。
 */
@Data
@TableName("ocr_file_split")
public class OcrFileSplit {

    /**
     * 使用 MP 雪花算法，与主表保持一致，避免 OceanBase JDBC RETURNING 问题。
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联的主文件ID（ocr_file_main.id）
     */
    @TableField("main_id")
    private Long mainId;

    /**
     * 拆分序号（从0开始，用于排序和聚合）
     */
    @TableField("split_index")
    private Integer splitIndex;

    /**
     * 对应原始文件的页码（PDF场景，从1开始；非PDF场景可为null）
     */
    @TableField("page_no")
    private Integer pageNo;

    /**
     * 分片文件完整存储路径（含 bucket），格式：bucket/path/to/page_1.jpg
     * 不含协议前缀，由业务层拼接后传入
     */
    @TableField("file_path")
    private String filePath;

    /**
     * 子文件类型：jpg / png / tiff 等
     */
    @TableField("file_type")
    private String fileType;

    /**
     * 子文件大小（字节）
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 子文件OCR状态：PENDING / PROCESSING / SUCCESS / FAILED
     */
    @TableField("ocr_status")
    private String ocrStatus;

    /**
     * 该分片使用的提示词（通常与主文件相同，支持按页定制）
     */
    @TableField("prompt")
    private String prompt;

    /**
     * 本分片的 OCR 识别结果文本（大智部等引擎）
     */
    @TableField("ocr_result")
    private String ocrResult;

    /**
     * 本分片大模型/多模态（正言 img2text）识别正文，与 {@link #ocrResult} 分列存储。
     */
    @TableField("llm_result")
    private String llmResult;

    /**
     * 该分片使用的图片 base64（排查/重跑用）。
     * 注意：可能非常大，使用 CLOB 存储。
     */
    @TableField("image_base64")
    private String imageBase64;

    /**
     * 本分片识别失败时的错误信息
     */
    @TableField("error_message")
    private String errorMessage;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
