package com.sinosig.aip.business.pipeline.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class FileItem {

    @Schema(description = "原始文件名（含扩展名）", required = true, example = "招标文件.pdf")
    private String fileName;

    @Schema(description = "文件类型", example = "pdf")
    private String fileType;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "MinIO 存储路径（bucket/path/to/file，不含协议头）", example = "aip-ocr/2026/03/招标文件.pdf")
    private String filePath;

    @Schema(description = "文件 base64 内容（与 filePath 二选一；传 base64 时直接使用，不从 MinIO 拉取）")
    private String base64Content;

    @Schema(description = "文件 SHA-256（可选，用于精确匹配加速）")
    private String sha256;
}
