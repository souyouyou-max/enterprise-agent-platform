package com.sinosig.aip.business.pipeline.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.sinosig.aip.data.entity.FileSimilarityResult;
import com.sinosig.aip.data.entity.OcrFileAnalysis;
import com.sinosig.aip.data.entity.OcrFileMain;
import com.sinosig.aip.data.entity.OcrPipelineBatch;

import java.util.List;

/**
 * OCR 流水线编排服务
 *
 * <h3>完整流水线阶段</h3>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────────────────────┐
 * │                          OCR 多文件处理流水线                                    │
 * │                                                                                 │
 * │  [1] submitBatch()                                                              │
 * │      上传多文件 → 创建 ocr_pipeline_batch（PENDING）                            │
 * │      → 创建 N 条 ocr_file_main（ocr_status=PENDING）                            │
 * │      → 异步触发 triggerOcrPhase()                                               │
 * │                                                                                 │
 * │  [2] triggerOcrPhase()                                                          │
 * │      批次状态 → OCR_PROCESSING                                                  │
 * │      → 各文件调用大智部 OCR 拆分分片（并行）                                    │
 * │      → 每文件完成后回调 onFilOcrDone()                                          │
 * │      → 全批 OCR 完成 → 批次状态 OCR_DONE → 触发 triggerAnalysisPhase()         │
 * │                                                                                 │
 * │  [3] triggerAnalysisPhase()                                                     │
 * │      批次状态 → ANALYZING                                                       │
 * │      → 各文件取分片 image_base64 → 调用正言大模型提取结构化字段（并行）         │
 * │        （印章/营业执照/报价金额/文档摘要等）                                    │
 * │      → 结果写入 ocr_file_analysis 表                                            │
 * │      → 全批分析完成 → 批次状态 ANALYZED → 触发 triggerComparePhase()           │
 * │                                                                                 │
 * │  [4] triggerComparePhase()                                                      │
 * │      批次状态 → COMPARING                                                       │
 * │      → 读 ocr_file_split.ocr_result 拼接文字 → Python 相似度对比               │
 * │      → 结果写入 file_similarity_result 表                                       │
 * │      → 批次状态 → DONE                                                          │
 * │                                                                                 │
 * │  [定时任务] OcrPipelineScheduler                                                │
 * │      轮询卡在各阶段超时的批次 → 补偿重跑                                       │
 * └─────────────────────────────────────────────────────────────────────────────────┘
 * </pre>
 */
public interface OcrPipelineService {

    /**
     * 提交多文件批次，注册记录并异步启动 OCR 阶段。
     *
     * @param batchNo  批次流水号（由调用方生成，建议格式：BATCH_yyyyMMddHHmmss_唯一后缀）
     * @param appCode  应用/租户编码
     * @param files    待处理的文件信息列表
     * @param extraInfo 调用方扩展 JSON（业务单号、申请人等，原样写入 batch）
     * @return 创建的批次记录
     */
    OcrPipelineBatch submitBatch(String batchNo, String appCode,
                                  List<PipelineFileInfo> files, String extraInfo);

    /**
     * 触发指定批次的 OCR 阶段（大智部 OCR 拆分 + 文字提取）。
     * 通常由 submitBatch 异步调用，也可由定时任务补偿触发。
     *
     * @param batchNo 批次流水号
     */
    void triggerOcrPhase(String batchNo);

    /**
     * 触发指定批次的多模态语义分析阶段（正言大模型）。
     * 分析内容：印章、营业执照、报价金额、文档类型、摘要等。
     * 通常在 OCR_DONE 后自动触发。
     *
     * @param batchNo 批次流水号
     */
    void triggerAnalysisPhase(String batchNo);

    /**
     * 触发指定批次的文件相似度对比阶段（Python 服务）。
     * 通常在 ANALYZED 后自动触发。
     *
     * @param batchNo 批次流水号
     */
    void triggerComparePhase(String batchNo);

    /**
     * 查询批次及其文件状态概况。
     *
     * @param batchNo 批次流水号
     * @return 批次进度视图
     */
    BatchProgressView getBatchProgress(String batchNo);

    /**
     * 手动重跑指定文件的 OCR 阶段（用于单文件失败重试）。
     *
     * @param mainId ocr_file_main.id
     */
    void retryOcrForFile(Long mainId);

    /**
     * 手动重跑指定文件的多模态分析阶段。
     *
     * @param mainId ocr_file_main.id
     */
    void retryAnalysisForFile(Long mainId);

    // ── 内部 DTO ──────────────────────────────────────────────────────────

    /**
     * 提交批次时传入的单个文件信息。
     */
    record PipelineFileInfo(
            /** 原始文件名（含扩展名） */
            String fileName,
            /** 文件类型（pdf / jpg / png / docx 等） */
            String fileType,
            /** 文件大小（字节） */
            Long fileSize,
            /** 文件存储路径（MinIO bucket/path/to/file，不含协议前缀） */
            String filePath,
            /** 文件 base64 内容（用于 OCR；存储已在 MinIO 时可不传，此时依赖 filePath 拉取） */
            String base64Content,
            /** 文件 SHA-256（可选，用于相似度对比快速精确匹配） */
            String sha256
    ) {}

    /**
     * 文件处理状态摘要（不含 CLOB 大字段，避免进度接口响应体过大）。
     */
    record FileStatusSummary(
            String id,
            String fileName,
            String fileType,
            Long   fileSize,
            String ocrStatus,
            String analysisStatus,
            String errorMessage
    ) {
        public static FileStatusSummary from(OcrFileMain m) {
            return new FileStatusSummary(
                    m.getId() == null ? null : String.valueOf(m.getId()),
                    m.getFileName(), m.getFileType(), m.getFileSize(),
                    m.getOcrStatus(), m.getAnalysisStatus(), m.getErrorMessage());
        }
    }

    /**
     * 分析结果摘要（不含 analysisRaw / analysisPrompt 等大字段）。
     */
    record AnalysisSummary(
            String  id,
            String  mainId,
            String  batchNo,
            String  docType,
            /** 优先取大模型输出的 doc_type_label（中文证件/文档类型），否则由 doc_type 推导 */
            String  docTypeLabel,
            Integer hasStamp,
            String  stampText,
            String  companyName,
            String  licenseNo,
            java.math.BigDecimal totalAmount,
            String  keyDates,
            String  docSummary,
            String  structuredExtra,
            String  status,
            String  errorMessage
    ) {
        private static final ObjectMapper ANALYSIS_SUMMARY_MAPPER = new ObjectMapper();

        public static AnalysisSummary from(OcrFileAnalysis a) {
            Integer hasStamp = a.getHasStamp() == null ? null : (a.getHasStamp() ? 1 : 0);
            return new AnalysisSummary(
                    a.getId() == null ? null : String.valueOf(a.getId()),
                    a.getMainId() == null ? null : String.valueOf(a.getMainId()),
                    a.getBatchNo(),
                    a.getDocType(), resolveDocTypeLabel(a), hasStamp, a.getStampText(),
                    a.getCompanyName(), a.getLicenseNo(), a.getTotalAmount(),
                    a.getKeyDates(), a.getDocSummary(), a.getStructuredExtra(),
                    a.getStatus(), a.getErrorMessage());
        }

        private static String resolveDocTypeLabel(OcrFileAnalysis a) {
            String extra = a.getStructuredExtra();
            if (extra != null && !extra.isBlank()) {
                try {
                    String t = ANALYSIS_SUMMARY_MAPPER.readTree(extra).path("doc_type_label").asText("");
                    if (!t.isBlank()) {
                        return t.trim();
                    }
                } catch (Exception ignored) {
                    /* 使用 doc_type 推导 */
                }
            }
            return OcrFileAnalysis.labelOfDocType(a.getDocType());
        }
    }

    /**
     * 相似度两两对比摘要（对应 {@link FileSimilarityResult}，不含 extra_detail 等大字段）。
     */
    record SimilarityPairSummary(
            String fileAName,
            String fileBName,
            /** TF-IDF 余弦（0~1），无则回退为 difflib ratio */
            Double textSimilarity,
            /**
             * 文件维度展示分（0~1）：优先 {@code file_visual_sim}（感知哈希）；
             * 若无视觉分，则用字节级信息推断——完全一致为 1，明确不同为 0，未知为 null。
             */
            Double fileSimilarity,
            /**
             * 综合分：仅当存在视觉相似度时与文字取算术平均；否则综合与「仅文字」一致，
             * 避免无视觉哈希时二进制 0/1 过度拉低综合分。
             */
            Double overallSimilarity
    ) {
        public static SimilarityPairSummary from(FileSimilarityResult r) {
            if (r == null) {
                return null;
            }
            Double tfidf = r.getTextTfidfCosine() != null ? r.getTextTfidfCosine().doubleValue() : null;
            Double difflib = r.getTextDifflibRatio() != null ? r.getTextDifflibRatio().doubleValue() : null;
            Double textSim = tfidf != null ? tfidf : difflib;
            Double visual = r.getFileVisualSim() != null ? r.getFileVisualSim().doubleValue() : null;
            Double fileDisplay = computeFileDisplaySimilarity(r, visual);
            Double overall = computeOverallSimilarity(textSim, tfidf, difflib, visual);
            return new SimilarityPairSummary(r.getFileAName(), r.getFileBName(), textSim, fileDisplay, overall);
        }

        /**
         * 视觉分优先；否则用库中字节级字段给出可解释的 0/1（与「看起来像不像」的感知分不同，仅代表是否同一字节）。
         */
        private static Double computeFileDisplaySimilarity(FileSimilarityResult r, Double visual) {
            if (visual != null) {
                return visual;
            }
            if (Boolean.TRUE.equals(r.getFileExactMatch())) {
                return 1.0;
            }
            String sa = r.getFileASha256();
            String sb = r.getFileBSha256();
            if (sa != null && sb != null && !sa.isBlank() && !sb.isBlank()) {
                return sa.equalsIgnoreCase(sb) ? 1.0 : 0.0;
            }
            if (Boolean.FALSE.equals(r.getFileExactMatch())) {
                return 0.0;
            }
            return null;
        }

        private static Double computeOverallSimilarity(
                Double textSim, Double tfidf, Double difflib, Double visual) {
            if (textSim == null) {
                return visual;
            }
            if (visual != null) {
                return (textSim + visual) / 2.0;
            }
            return (tfidf != null && difflib != null) ? Math.max(tfidf, difflib) : textSim;
        }
    }

    /**
     * 批次进度视图（供 API 查询返回，所有字段均为轻量 DTO，无 CLOB 大字段）。
     */
    record BatchSummary(
            String id,
            String batchNo,
            String status,
            Integer totalFiles,
            Integer ocrDoneFiles,
            Integer analysisDoneFiles,
            String errorMessage,
            java.time.LocalDateTime updatedAt
    ) {
        public static BatchSummary from(OcrPipelineBatch b) {
            if (b == null) {
                return null;
            }
            return new BatchSummary(
                    b.getId() == null ? null : String.valueOf(b.getId()),
                    b.getBatchNo(),
                    b.getStatus(),
                    b.getTotalFiles(),
                    b.getOcrDoneFiles(),
                    b.getAnalysisDoneFiles(),
                    b.getErrorMessage(),
                    b.getUpdatedAt()
            );
        }
    }

    record BatchProgressView(
            BatchSummary       batch,
            List<FileStatusSummary> files,
            List<AnalysisSummary>  analyses,
            int totalFiles,
            int ocrDone,
            int analysisDone,
            boolean compareFinished,
            List<SimilarityPairSummary> similarityPairs
    ) {}
}
