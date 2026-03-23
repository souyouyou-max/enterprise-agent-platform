package com.enterprise.agent.business.pipeline.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class SubmitBatchRequest {

    @Schema(description = "批次流水号（可选，不传则自动生成，格式 BATCH_yyyyMMddHHmmss_XXXXXXXX）")
    private String batchNo;

    @Schema(description = "应用/租户编码")
    private String appCode;

    @Schema(description = "待处理文件列表（至少 1 个）。单文件仅做 OCR/语义分析；相似度对比需至少 2 个 OCR 成功的文件，否则自动跳过对比阶段。")
    private List<FileItem> files;

    @Schema(description = "扩展 JSON，原样写入批次。可选：ocrEnabled、analysisEnabled、compareEnabled、maxImagesPerFile（单次 img2text 请求最多几张图）、maxTotalAnalysisPages（语义分析最多处理多少页分片）、failToleranceRatio；多模态话术模板 multimodalPromptKeys（字符串数组，多套合并一次调用）优先于单键 multimodalPromptKey。示例：{\"multimodalPromptKeys\":[\"certificates\",\"id_card\"]}")
    private String extraInfo;
}
