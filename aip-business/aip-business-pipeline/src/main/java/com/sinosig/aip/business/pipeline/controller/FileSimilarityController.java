package com.sinosig.aip.business.pipeline.controller;

import com.sinosig.aip.business.pipeline.service.FileSimilarityService;
import com.sinosig.aip.common.core.response.ResponseResult;
import com.sinosig.aip.data.entity.FileSimilarityResult;
import com.sinosig.aip.data.mapper.FileSimilarityResultMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 多附件文件相似度对比 API
 *
 * <p>对话场景下用户上传多个附件后，调用此接口进行文件相似度对比：
 * <ul>
 *   <li><b>文字相似度</b>：读取 {@code ocr_file_split.ocr_result}（分段OCR识别结果）
 *       调用 Python bid-analysis-service 进行 TF-IDF + difflib 对比。</li>
 *   <li><b>文件整体相似度</b>：对整个文件做 SHA-256 精确匹配 + PDF/图片感知哈希均值。</li>
 * </ul>
 * <p>对比结果自动写入 {@code file_similarity_result} 表（入库）。
 */
@Tag(name = "多附件相似度对比", description = "对话多附件文件相似度对比（文字相似度 + 文件整体相似度），结果入库")
@RestController
@RequestMapping("/api/v1/aip/semantics")
@RequiredArgsConstructor
@Validated
@Slf4j
public class FileSimilarityController {

    private final FileSimilarityService fileSimilarityService;
    private final FileSimilarityResultMapper fileSimilarityResultMapper;

    /**
     * 多附件相似度对比（主接口）
     *
     * <p>请求中传入已完成 OCR 的文件主表 ID 列表（至少 2 个），接口将：
     * <ol>
     *   <li>读取每个文件的分段 OCR 文字（{@code ocr_file_split.ocr_result}）；</li>
     *   <li>调用 Python 服务进行文字相似度对比；</li>
     *   <li>返回文件整体相似度（SHA-256 + 感知哈希）；</li>
     *   <li>将所有两两对比结果写入 {@code file_similarity_result} 表。</li>
     * </ol>
     */
    @Operation(summary = "多附件相似度对比",
            description = "传入已OCR的文件主表ID列表，返回两两文字相似度（分段识别）和文件整体相似度，结果自动入库")
    @PostMapping("/compare-attachments")
    public ResponseResult<CompareAttachmentsResponse> compareAttachments(
            @Valid @RequestBody CompareAttachmentsRequest req) {

        String businessNo = req.getBusinessNo() != null && !req.getBusinessNo().isBlank()
                ? req.getBusinessNo()
                : UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        log.info("[FileSimilarityController] compare-attachments start businessNo={} mainIds={} appCode={}",
                businessNo, req.getMainIds(), req.getAppCode());

        List<FileSimilarityResult> results = fileSimilarityService.compareByMainIds(
                req.getMainIds(), businessNo, req.getAppCode());

        log.info("[FileSimilarityController] compare-attachments done businessNo={} pairs={}",
                businessNo, results.size());

        CompareAttachmentsResponse resp = new CompareAttachmentsResponse();
        resp.setBusinessNo(businessNo);
        resp.setTotalPairs(results.size());
        resp.setResults(results);
        return ResponseResult.success(resp);
    }

    /**
     * 查询指定 business_no 的对比结果
     */
    @Operation(summary = "查询对比结果", description = "根据业务流水号查询已入库的文件相似度对比结果")
    @GetMapping("/compare-attachments/{businessNo}")
    public ResponseResult<List<FileSimilarityResult>> getCompareResults(
            @Parameter(description = "业务流水号") @PathVariable("businessNo") String businessNo) {
        return ResponseResult.success(fileSimilarityResultMapper.findByBusinessNo(businessNo));
    }

    /**
     * 查询指定文件（ocr_file_main.id）关联的所有对比记录
     */
    @Operation(summary = "查询文件对比历史", description = "根据 ocr_file_main.id 查询该文件参与的全部相似度对比记录")
    @GetMapping("/compare-attachments/by-file/{mainId}")
    public ResponseResult<List<FileSimilarityResult>> getByFileId(
            @Parameter(description = "ocr_file_main.id") @PathVariable("mainId") Long mainId) {
        return ResponseResult.success(fileSimilarityResultMapper.findByMainFileId(mainId));
    }

    // ── Request / Response DTO ────────────────────────────────────────────

    @Data
    public static class CompareAttachmentsRequest {

        @Schema(description = "业务流水号（可选，不传则自动生成）")
        private String businessNo;

        @NotEmpty(message = "mainIds 不能为空")
        @Size(min = 2, message = "至少需要 2 个文件才能进行相似度对比")
        @Schema(description = "已完成OCR的 ocr_file_main.id 列表（至少2个）", requiredMode = Schema.RequiredMode.REQUIRED)
        private List<Long> mainIds;

        @Schema(description = "应用编码（可选）")
        private String appCode;
    }

    @Data
    public static class CompareAttachmentsResponse {
        private String businessNo;
        private int totalPairs;
        /** 两两对比结果列表（已入库） */
        private List<FileSimilarityResult> results;
    }
}
