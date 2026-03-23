package com.enterprise.agent.business.pipeline.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Data;

/**
 * OCR 识别请求 DTO
 * <p>
 * 封装一次识别任务所需的文件元信息 + 识别引擎原始请求体。
 * 调用方在触发识别前填充此对象，由 {@link OcrRecognitionService} 负责入库和调用引擎。
 */
@Data
@Builder
public class OcrRecognitionRequest {

    /**
     * 业务流水号（全局唯一，用作幂等键）。
     * 同一 businessNo 的识别任务若已存在，可在业务层做幂等判断。
     */
    private String businessNo;

    /**
     * 识别来源：DAZHI_OCR（大智部）/ ZHENGYAN_MULTIMODAL（正言多模态）
     */
    private String source;

    /**
     * 原始文件名（含扩展名，如 invoice_2024.pdf）
     */
    private String fileName;

    /**
     * 文件类型（pdf / jpg / png / tiff 等）
     */
    private String fileType;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件在 MinIO 中的完整路径（格式：bucket/path/to/file，不含协议前缀）
     */
    private String filePath;

    /**
     * 应用编码（大智部 appCode / 正言 appId）
     */
    private String appCode;

    /**
     * 扩展信息（JSON 字符串，存储自定义业务字段）
     */
    private String extraInfo;

    /**
     * 发给识别引擎的提示词。
     * <ul>
     *   <li>ZHENGYAN_MULTIMODAL：作为 img2text messages 中的用户文本，描述识别意图</li>
     *   <li>DAZHI_OCR：若引擎支持则传入，否则仅入库留存</li>
     * </ul>
     */
    private String prompt;

    /**
     * 传递给识别引擎的原始请求体（JSON）。
     * 调用方按各引擎 API 约定组装，{@link OcrRecognitionService} 不修改其内容，直接透传给引擎。
     */
    private ObjectNode engineRequest;
}
