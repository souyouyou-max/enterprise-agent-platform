package com.enterprise.agent.business.chat.controller;

import com.enterprise.agent.common.core.response.ResponseResult;
import com.enterprise.agent.data.entity.OcrFileMain;
import com.enterprise.agent.data.entity.OcrFileSplit;
import com.enterprise.agent.data.service.OcrFileDataService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理后台：OCR 主表/分片预览。
 * 仅用于展示数据库中已落库的识别结果与分片详情，不读取 MinIO 原始文件。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/ocr")
public class OcrAdminPreviewController {

    private final OcrFileDataService ocrFileDataService;

    @GetMapping("/files")
    public ResponseResult<List<OcrFileMainPreviewDTO>> listOcrFiles(
            @RequestParam(name = "source", required = false) String source,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "limit", defaultValue = "50") Integer limit) {
        List<OcrFileMain> mains = ocrFileDataService.findMainList(source, status, limit);
        List<OcrFileMainPreviewDTO> dtos = new ArrayList<>(mains.size());
        for (OcrFileMain m : mains) {
            dtos.add(OcrFileMainPreviewDTO.from(m));
        }
        return ResponseResult.success(dtos);
    }

    @GetMapping("/files/{mainId}/splits")
    public ResponseResult<List<OcrFileSplitPreviewDTO>> listOcrSplits(
            @PathVariable(name = "mainId") Long mainId,
            @RequestParam(name = "includeImageBase64", defaultValue = "false") boolean includeImageBase64) {
        List<OcrFileSplit> splits = ocrFileDataService.findSplitsByMainId(mainId);
        List<OcrFileSplitPreviewDTO> dtos = new ArrayList<>(splits.size());
        for (OcrFileSplit s : splits) {
            dtos.add(OcrFileSplitPreviewDTO.from(s, includeImageBase64));
        }
        return ResponseResult.success(dtos);
    }

    @Data
    public static class OcrFileMainPreviewDTO {
        // Long id values can exceed JS Number safe integer range (~9e15)，因此直接用 String 返回避免精度丢失。
        private String id;
        private String businessNo;
        private String source;
        private String fileName;
        private String fileType;
        private Long fileSize;
        private String ocrStatus;
        private Integer totalPages;
        private String errorMessage;
        private String ocrResultPreview;
        private String prompt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        static OcrFileMainPreviewDTO from(OcrFileMain m) {
            OcrFileMainPreviewDTO dto = new OcrFileMainPreviewDTO();
            dto.setId(m.getId() == null ? null : String.valueOf(m.getId()));
            dto.setBusinessNo(m.getBusinessNo());
            dto.setSource(m.getSource());
            dto.setFileName(m.getFileName());
            dto.setFileType(m.getFileType());
            dto.setFileSize(m.getFileSize());
            dto.setOcrStatus(m.getOcrStatus());
            dto.setTotalPages(m.getTotalPages());
            dto.setErrorMessage(m.getErrorMessage());
            dto.setPrompt(m.getPrompt());
            dto.setCreatedAt(m.getCreatedAt());
            dto.setUpdatedAt(m.getUpdatedAt());
            dto.setOcrResultPreview(truncate(m.getOcrResult(), 2000));
            return dto;
        }
    }

    @Data
    public static class OcrFileSplitPreviewDTO {
        private String id;
        private Integer splitIndex;
        private Integer pageNo;
        private String filePath;
        private String fileType;
        private Long fileSize;
        private String ocrStatus;
        private String errorMessage;
        private String prompt;
        private String ocrResult;
        private String imageBase64;

        static OcrFileSplitPreviewDTO from(OcrFileSplit s, boolean includeImageBase64) {
            OcrFileSplitPreviewDTO dto = new OcrFileSplitPreviewDTO();
            dto.setId(s.getId() == null ? null : String.valueOf(s.getId()));
            dto.setSplitIndex(s.getSplitIndex());
            dto.setPageNo(s.getPageNo());
            dto.setFilePath(s.getFilePath());
            dto.setFileType(s.getFileType());
            dto.setFileSize(s.getFileSize());
            dto.setOcrStatus(s.getOcrStatus());
            dto.setErrorMessage(s.getErrorMessage());
            dto.setPrompt(s.getPrompt());
            dto.setOcrResult(s.getOcrResult());
            dto.setImageBase64(includeImageBase64 ? s.getImageBase64() : null);
            return dto;
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}

