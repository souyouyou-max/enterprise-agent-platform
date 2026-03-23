package com.enterprise.agent.business.pipeline.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.enterprise.agent.business.pipeline.config.PipelineEffectiveConfig;
import com.enterprise.agent.business.pipeline.config.PipelineEffectiveConfigResolver;
import com.enterprise.agent.business.pipeline.config.PipelineMultimodalPromptResolver;
import com.enterprise.agent.business.pipeline.service.DocumentImageConverter;
import com.enterprise.agent.business.pipeline.service.FileSimilarityService;
import com.enterprise.agent.business.pipeline.service.MultimodalService;
import com.enterprise.agent.business.pipeline.service.OcrPipelineService;
import com.enterprise.agent.business.pipeline.service.impl.PipelineLlmAnalysisDomainService;
import com.enterprise.agent.business.pipeline.service.OcrPipelineService.AnalysisSummary;
import com.enterprise.agent.business.pipeline.service.OcrPipelineService.BatchProgressView;
import com.enterprise.agent.business.pipeline.service.OcrPipelineService.FileStatusSummary;
import com.enterprise.agent.business.pipeline.service.OcrPipelineService.SimilarityPairSummary;
import com.enterprise.agent.data.entity.FileSimilarityResult;
import com.enterprise.agent.data.entity.OcrFileAnalysis;
import com.enterprise.agent.data.entity.OcrFileMain;
import com.enterprise.agent.data.entity.OcrFileSplit;
import com.enterprise.agent.data.entity.OcrPipelineBatch;
import com.enterprise.agent.data.mapper.FileSimilarityResultMapper;
import com.enterprise.agent.data.mapper.OcrFileAnalysisMapper;
import com.enterprise.agent.data.mapper.OcrPipelineBatchMapper;
import com.enterprise.agent.data.service.OcrFileDataService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * OCR 流水线编排服务实现
 *
 * <h3>流水线阶段</h3>
 * <pre>
 * submitBatch()
 *   └─ 创建 ocr_pipeline_batch + N 条 ocr_file_main（均为 PENDING）
 *   └─ 异步触发 triggerOcrPhase()
 *
 * triggerOcrPhase()
 *   └─ 逐文件：markMainProcessing → callDazhiOcr → 持久化分片 → markSuccess/Failed
 *   └─ 全批完成 → OCR_DONE → triggerAnalysisPhase()（或 ANALYZED 若分析关闭）
 *
 * triggerAnalysisPhase()
 *   └─ 逐文件：读 image_base64 → 调正言 img2text（可按页/按块多次）→ 解析 JSON → 写 ocr_file_analysis；
 *       并将各批/各分片大模型正文回写 ocr_file_split.llm_result
 *   └─ 全批完成 → ANALYZED → triggerComparePhase()
 *
 * triggerComparePhase()
 *   └─ 读各文件 ocr_file_split.ocr_result → 调 Python 相似度服务
 *   └─ 写 file_similarity_result → DONE
 * </pre>
 *
 * <h3>关键设计说明</h3>
 * <ul>
 *   <li>OCR 阶段直接调 {@link MultimodalService#dazhiOcrGeneral} 并通过
 *       {@link OcrFileDataService} 持久化，不调 OcrRecognitionService.recognize()
 *       以避免重复创建 ocr_file_main 记录（submitBatch 已建）。</li>
 *   <li>file base64 存入 extra_info，便于 OCR 阶段读取；项目接入 MinIO 后可改为按路径拉取。</li>
 *   <li>批次状态均走 CAS 更新，防止定时任务与 API 并发覆盖。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrPipelineServiceImpl implements OcrPipelineService {

    /** 日志中单段话术 / 正文 / JSON 的最大字符数，避免撑爆日志与采集端 */
    private static final int PIPELINE_LLM_LOG_MAX_CHARS = 100_000;

    // ── 依赖注入（接口而非具体实现类） ──────────────────────────────────────
    private final PipelineEffectiveConfigResolver pipelineEffectiveConfigResolver;
    private final PipelineMultimodalPromptResolver multimodalPromptResolver;
    private final OcrFileDataService     ocrFileDataService;
    private final DocumentImageConverter documentImageConverter;
    private final MultimodalService      multimodalService;       // 接口，非 Impl
    private final PipelineLlmAnalysisDomainService llmAnalysisDomainService;
    private final FileSimilarityService  fileSimilarityService;
    private final OcrPipelineBatchMapper     batchMapper;
    private final OcrFileAnalysisMapper      analysisMapper;
    private final FileSimilarityResultMapper fileSimilarityResultMapper;
    private final ObjectMapper               objectMapper;

    // ═════════════════════════════════════════════════════════════════════════
    // [1] 提交批次
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public OcrPipelineBatch submitBatch(String batchNo, String appCode,
                                         List<PipelineFileInfo> files, String extraInfo) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("批次至少需要 1 个文件");
        }

        // 创建批次记录
        OcrPipelineBatch batch = new OcrPipelineBatch();
        batch.setBatchNo(batchNo);
        batch.setAppCode(appCode);
        batch.setTotalFiles(files.size());
        batch.setOcrDoneFiles(0);
        batch.setAnalysisDoneFiles(0);
        batch.setStatus(OcrPipelineBatch.STATUS_PENDING);
        batch.setTriggerSource(OcrPipelineBatch.SOURCE_API);
        batch.setExtraInfo(extraInfo);
        batchMapper.insert(batch);
        log.info("[Pipeline] 批次提交 batchNo={}, totalFiles={}, appCode={}", batchNo, files.size(), appCode);

        // 为每个文件创建 ocr_file_main 记录；base64 存入 extra_info 供 OCR 阶段使用
        for (int i = 0; i < files.size(); i++) {
            PipelineFileInfo f = files.get(i);
            OcrFileMain main = new OcrFileMain();
            main.setBatchNo(batchNo);
            // 每个文件的 business_no 在批次内唯一（批次号 + 序号）
            main.setBusinessNo(batchNo + "_" + i);
            main.setSource("DAZHI_OCR");
            main.setFileName(f.fileName());
            main.setFileType(f.fileType());
            main.setFileSize(f.fileSize());
            main.setFilePath(f.filePath());
            main.setOcrStatus(OcrFileDataService.STATUS_PENDING);
            main.setAnalysisStatus(OcrFileAnalysis.STATUS_PENDING);
            main.setAppCode(appCode);
            // 将 sha256 与 base64 一起存入 extra_info（CLOB 无大小限制）
            main.setExtraInfo(buildExtraInfo(f));
            ocrFileDataService.createMain(main);
        }

        // 异步启动 OCR 阶段（当前线程立即返回给调用方）；命名 pipeline 可单独关闭 ocr
        if (pipelineEffectiveConfigResolver.resolve(extraInfo).ocrEnabled()) {
            triggerOcrPhaseAsync(batchNo);
        } else {
            log.warn("[Pipeline] 本 pipeline 未启用 OCR，未自动启动，请手动触发 triggerOcrPhase batchNo={}", batchNo);
        }
        return batchMapper.findByBatchNo(batchNo);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // [2] OCR 阶段（大智部）
    // ═════════════════════════════════════════════════════════════════════════

    @Async
    public void triggerOcrPhaseAsync(String batchNo) {
        triggerOcrPhase(batchNo);
    }

    @Override
    public void triggerOcrPhase(String batchNo) {
        OcrPipelineBatch batch = batchMapper.findByBatchNo(batchNo);
        if (batch == null) {
            log.warn("[Pipeline] OCR 触发失败，批次不存在 batchNo={}", batchNo);
            return;
        }
        int cas = batchMapper.casStatus(batch.getId(),
                OcrPipelineBatch.STATUS_PENDING, OcrPipelineBatch.STATUS_OCR_PROCESSING);
        if (cas == 0) {
            log.info("[Pipeline] OCR 阶段已被触发，跳过 batchNo={} currentStatus={}", batchNo, batch.getStatus());
            return;
        }
        log.info("[Pipeline] OCR 阶段开始 batchNo={}", batchNo);

        List<OcrFileMain> mains = ocrFileDataService.findMainByBatchNo(batchNo);
        int failCount = 0;

        for (OcrFileMain main : mains) {
            try {
                performOcrForFile(main);
            } catch (Exception e) {
                log.error("[Pipeline] 文件 OCR 失败 mainId={} fileName={}: {}",
                        main.getId(), main.getFileName(), e.getMessage(), e);
                ocrFileDataService.markMainFailed(main.getId(), truncate(e.getMessage(), 900));
                failCount++;
            }
        }

        // 判断是否超过容忍阈值
        int total = mains.size();
        long ocrSuccess = ocrFileDataService.countMainByBatchAndOcrStatus(batchNo, OcrFileDataService.STATUS_SUCCESS);
        if (ocrSuccess == 0) {
            markBatchFailed(batch.getId(), "所有文件 OCR 均失败（共 " + total + " 个）");
            return;
        }
        PipelineEffectiveConfig eff = pipelineEffectiveConfigResolver.resolve(batch.getExtraInfo());
        double tol = eff.failToleranceRatio();
        if ((double) failCount / total > tol) {
            markBatchFailed(batch.getId(), "OCR 失败率（" + failCount + "/" + total + "）超过容忍阈值");
            return;
        }

        String ocrDoneStatus = failCount > 0 ? OcrPipelineBatch.STATUS_PARTIAL_FAIL : OcrPipelineBatch.STATUS_OCR_DONE;
        batchMapper.casStatus(batch.getId(), OcrPipelineBatch.STATUS_OCR_PROCESSING, ocrDoneStatus);
        log.info("[Pipeline] OCR 阶段完成 batchNo={} success={}/{} nextStatus={}",
                batchNo, ocrSuccess, total, ocrDoneStatus);

        // 顺序固定：OCR → 分析 → 对比；全局 eap.pipeline 与 extra_info 合并后的开关决定跳过
        if (eff.analysisEnabled()) {
            batchMapper.casStatus(batch.getId(), ocrDoneStatus, OcrPipelineBatch.STATUS_ANALYZING);
            triggerAnalysisPhase(batchNo);
        } else if (eff.compareEnabled()) {
            batchMapper.casStatus(batch.getId(), ocrDoneStatus, OcrPipelineBatch.STATUS_ANALYZED);
            triggerComparePhase(batchNo);
        } else {
            markBatchDone(batch.getId());
            log.info("[Pipeline] 语义分析与相似度均已关闭，批次在 OCR 后直接结束 batchNo={}", batchNo);
        }
    }

    /**
     * 对单个文件执行大智部 OCR，结果写入 ocr_file_split 并更新 ocr_file_main。
     * <p>
     * <b>不调用 OcrRecognitionService.recognize()</b>，因为 submitBatch() 已创建 main 记录，
     * recognize() 会重复 INSERT 导致 business_no 唯一约束冲突。
     */
    private void performOcrForFile(OcrFileMain main) throws Exception {
        ocrFileDataService.markMainProcessing(main.getId());

        // 从 extra_info 读取 base64 内容
        String base64 = readBase64FromExtraInfo(main.getExtraInfo());
        if (base64 == null || base64.isBlank()) {
            // 无 base64：标记为跳过（实际项目中可改为从 MinIO 按 filePath 拉取）
            log.warn("[Pipeline] 文件无 base64 内容，OCR 跳过 mainId={} filePath={}", main.getId(), main.getFilePath());
            ocrFileDataService.saveDirectResult(main, null, "", "");
            return;
        }

        // 构造大智部 OCR 请求（attachments 格式，multimodalService 内部会按页拆分）
        ObjectNode engineReq = buildDazhiEngineRequest(main.getFileName(),
                guessContentType(main.getFileType()), base64);

        log.info("[Pipeline] 调用大智部 OCR mainId={} fileName={}", main.getId(), main.getFileName());
        JsonNode result = multimodalService.dazhiOcrGeneral(engineReq);

        // 持久化 OCR 结果（复用 OcrFileDataService，不重建 main）
        persistDazhiResultForMain(main, result);
    }

    private ObjectNode buildDazhiEngineRequest(String fileName, String mimeType, String base64) {
        ObjectNode req = objectMapper.createObjectNode();
        ArrayNode attachments = objectMapper.createArrayNode();
        ObjectNode att = objectMapper.createObjectNode();
        att.put("name", fileName);
        att.put("mimeType", mimeType);
        att.put("base64", base64);
        attachments.add(att);
        req.set("attachments", attachments);
        return req;
    }

    /**
     * 将大智部 OCR 结果持久化到已有的 main 记录中（不新建 main）。
     * 逻辑与 OcrRecognitionServiceImpl.persistDazhiFromResult 相同，
     * 但传入的是已存在的 OcrFileMain 实体。
     */
    private void persistDazhiResultForMain(OcrFileMain main, JsonNode result) {
        String prompt = null; // pipeline 场景下无特定 prompt
        JsonNode pages = result == null ? null : result.path("pages");
        boolean isMultiPage = pages != null && pages.isArray() && pages.size() > 1;

        if (isMultiPage) {
            List<OcrFileSplit> splits = buildSplitSkeletons(main, pages.size());
            for (int i = 0; i < pages.size() && i < splits.size(); i++) {
                splits.get(i).setImageBase64(pages.get(i).path("imageBase64").asText(""));
            }
            ocrFileDataService.saveSplits(main.getId(), splits);

            for (int i = 0; i < pages.size(); i++) {
                OcrFileSplit split = splits.get(i);
                JsonNode page = pages.get(i);
                boolean ok = page.path("success").asBoolean(false);
                if (ok) {
                    String text = extractDazhiText(page);
                    ocrFileDataService.saveSplitResult(split.getId(), main.getId(), prompt, text);
                } else {
                    String err = page.path("message").asText("大智部页面识别失败");
                    ocrFileDataService.markSplitFailed(split.getId(), main.getId(), err);
                    return; // 任一页失败即停止（主表已被 markSplitFailed 标记）
                }
            }
        } else {
            String content = result == null ? "" : result.path("content").asText("");
            String imageBase64 = "";
            if (result != null) {
                JsonNode p = result.path("pages");
                if (p.isArray() && p.size() > 0) imageBase64 = p.get(0).path("imageBase64").asText("");
            }
            ocrFileDataService.saveDirectResult(main, prompt, content, imageBase64);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // [3] 多模态语义分析阶段（正言大模型）
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public void triggerAnalysisPhase(String batchNo) {
        log.info("[Pipeline] 语义分析阶段开始 batchNo={}", batchNo);
        OcrPipelineBatch batch = batchMapper.findByBatchNo(batchNo);
        if (batch == null) return;

        PipelineEffectiveConfig eff = pipelineEffectiveConfigResolver.resolve(batch.getExtraInfo());

        if (!eff.analysisEnabled()) {
            log.info("[Pipeline] 本批次 pipeline 未启用语义分析，跳过 analysis batchNo={}", batchNo);
            if (eff.compareEnabled()) {
                promoteToAnalyzedForCompare(batch);
                triggerComparePhase(batchNo);
            } else {
                markBatchDone(batch.getId());
            }
            return;
        }

        log.info("[Pipeline] 本批次 multimodalTemplateKeys={}", eff.multimodalTemplateKeys());

        List<OcrFileMain> mains = ocrFileDataService.findMainByBatchNo(batchNo);

        for (OcrFileMain main : mains) {
            if (!OcrFileDataService.STATUS_SUCCESS.equals(main.getOcrStatus())) {
                // OCR 失败的文件跳过分析
                ocrFileDataService.markAnalysisSkipped(main.getId());
                saveSkippedAnalysis(main);
                continue;
            }
            try {
                performAnalysisForFile(main, eff);
                batchMapper.incrementAnalysisDone(batch.getId(), OcrPipelineBatch.STATUS_ANALYZING);
            } catch (Exception e) {
                log.error("[Pipeline] 语义分析失败 mainId={} fileName={}: {}",
                        main.getId(), main.getFileName(), e.getMessage(), e);
                ocrFileDataService.markAnalysisFailed(main.getId(), truncate(e.getMessage(), 900));
                saveFailedAnalysis(main, e.getMessage());
            }
        }

        batchMapper.casStatus(batch.getId(), OcrPipelineBatch.STATUS_ANALYZING, OcrPipelineBatch.STATUS_ANALYZED);
        log.info("[Pipeline] 语义分析阶段完成 batchNo={}", batchNo);

        if (eff.compareEnabled()) {
            triggerComparePhase(batchNo);
        } else {
            markBatchDone(batch.getId());
            log.info("[Pipeline] 相似度对比未启用，批次在语义分析后直接结束 batchNo={}", batchNo);
        }
    }

    /**
     * 在跳过语义分析、但需要相似度对比时，将批次状态推进到 ANALYZED，以便 {@link #triggerComparePhase} 做 CAS。
     */
    private void promoteToAnalyzedForCompare(OcrPipelineBatch batch) {
        String st = batch.getStatus();
        if (OcrPipelineBatch.STATUS_ANALYZED.equals(st)) {
            return;
        }
        int n = 0;
        if (OcrPipelineBatch.STATUS_OCR_DONE.equals(st) || OcrPipelineBatch.STATUS_PARTIAL_FAIL.equals(st)) {
            n = batchMapper.casStatus(batch.getId(), st, OcrPipelineBatch.STATUS_ANALYZED);
        } else if (OcrPipelineBatch.STATUS_ANALYZING.equals(st)) {
            n = batchMapper.casStatus(batch.getId(), OcrPipelineBatch.STATUS_ANALYZING, OcrPipelineBatch.STATUS_ANALYZED);
        }
        if (n == 0 && !OcrPipelineBatch.STATUS_ANALYZED.equals(st)) {
            log.warn("[Pipeline] 无法将批次推进到 ANALYZED（当前 status={}）batchNo={}", st, batch.getBatchNo());
        }
    }

    /**
     * 对单个文件调用正言大模型进行语义分析，结果解析后写入 ocr_file_analysis。
     *
     * <h4>入库字段</h4>
     * doc_type / has_stamp / stamp_text / company_name / license_no /
     * total_amount / key_dates / doc_summary / analysis_raw / status
     */
    private void performAnalysisForFile(OcrFileMain main, PipelineEffectiveConfig eff) throws Exception {
        ocrFileDataService.markAnalysisProcessing(main.getId());

        // 读取分片：按 split_index 顺序取前 maxTotalAnalysisPages 页；若某页缺 image_base64（大智部常只回写首页），
        // 则从主文件 extra_info 整文件 base64 按页渲染，保证多模态与 OCR 页数对齐。
        List<OcrFileSplit> splits = ocrFileDataService.findSplitsByMainId(main.getId());
        AnalysisImageInputs inputs = resolveAnalysisImageInputs(main, splits, eff.maxTotalAnalysisPages());
        List<OcrFileSplit> usedSplits = inputs.usedSplits();
        List<String> imageBase64s = inputs.imageBase64s();

        if (imageBase64s.isEmpty()) {
            // 无图片内容（如纯文本文件），跳过分析
            ocrFileDataService.markAnalysisSkipped(main.getId());
            saveSkippedAnalysis(main);
            log.info("[Pipeline] 语义分析跳过（无图片分片）mainId={}", main.getId());
            return;
        }

        String promptText = multimodalPromptResolver.resolveMergedByKeys(eff.multimodalTemplateKeys());
        String promptKey = String.join(",", eff.multimodalTemplateKeys());

        int chunkSize = Math.max(1, eff.maxImagesPerFile());
        int totalChunks = (usedSplits.size() + chunkSize - 1) / chunkSize;
        log.info("[Pipeline] 正言 img2text 话术 mainId={} fileName={} promptKey={} promptChars={} totalPages={} imagesPerCall={} expectedCalls={}\n{}",
                main.getId(), main.getFileName(), promptKey, promptText.length(),
                usedSplits.size(), chunkSize, totalChunks,
                logTruncatedForLlm(promptText));

        List<String> rawContents = new ArrayList<>();

        for (int start = 0; start < usedSplits.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, usedSplits.size());
            List<OcrFileSplit> subSplits = usedSplits.subList(start, end);
            List<String> subImages = imageBase64s.subList(start, end);
            int chunkNo = start / chunkSize + 1;

            ObjectNode engineReq = llmAnalysisDomainService.buildZhengyanAnalysisRequest(subImages, main.getFileName(), promptText);

            log.info("[Pipeline] 调用正言语义分析 mainId={} fileName={} chunk={}/{} pageRange={}-{} imageCount={} promptKey={}",
                    main.getId(), main.getFileName(), chunkNo, totalChunks, start + 1, end, subImages.size(), promptKey);

            ObjectNode result = multimodalService.img2Text(engineReq);

            boolean success = result.path("success").asBoolean(false);
            String rawContent = result.path("content").asText("");
            String extractedDocType = extractJsonFieldFromRaw(rawContent, "doc_type");
            String extractedDocTypeLabel = extractJsonFieldFromRaw(rawContent, "doc_type_label");
            if (extractedDocTypeLabel == null || extractedDocTypeLabel.isBlank()) {
                extractedDocTypeLabel = (extractedDocType != null && !extractedDocType.isBlank())
                        ? OcrFileAnalysis.labelOfDocType(extractedDocType)
                        : "—";
            }
            log.info("[Pipeline] 正言 img2text 返回 mainId={} fileName={} chunk={}/{} success={} contentChars={} docType={} docTypeLabel={}\ncontent={}\nrawJson={}",
                    main.getId(), main.getFileName(), chunkNo, totalChunks, success, rawContent.length(),
                    extractedDocType != null && !extractedDocType.isBlank() ? extractedDocType : "—",
                    extractedDocTypeLabel,
                    logTruncatedForLlm(rawContent),
                    logTruncatedForLlm(safeImg2TextResultJson(result)));

            if (!success || rawContent.isBlank()) {
                String errMsg = result.path("result").asText("正言分析返回空内容");
                throw new RuntimeException("正言 img2text 调用失败 chunk " + chunkNo + "/" + totalChunks + ": " + errMsg);
            }

            rawContents.add(rawContent);
            llmAnalysisDomainService.persistMultimodalResultsToSplits(main, subSplits, subImages, result, rawContent);
        }

        OcrFileAnalysis analysis = llmAnalysisDomainService.parseAndSaveAnalysis(main, rawContents, promptText);
        log.info("[Pipeline] 语义分析入库 mainId={} docType={} docTypeLabel={} hasStamp={} company={} licenseNo={} totalAmount={}",
                main.getId(), analysis.getDocType(), AnalysisSummary.from(analysis).docTypeLabel(),
                analysis.getHasStamp(),
                analysis.getCompanyName(), analysis.getLicenseNo(), analysis.getTotalAmount());

        ocrFileDataService.markAnalysisSuccess(main.getId());
    }

    /**
     * 解析多模态要用的图：优先用分片自带 image_base64；前 N 页任缺则从主文件 extra_info 按页展开。
     */
    private AnalysisImageInputs resolveAnalysisImageInputs(OcrFileMain main, List<OcrFileSplit> splits, int maxTotalPages)
            throws Exception {
        if (splits == null || splits.isEmpty()) {
            return new AnalysisImageInputs(List.of(), List.of());
        }
        int take = Math.min(splits.size(), Math.max(0, maxTotalPages));
        boolean anyBlankInFirst = false;
        for (int i = 0; i < take; i++) {
            String b = splits.get(i).getImageBase64();
            if (b == null || b.isBlank()) {
                anyBlankInFirst = true;
                break;
            }
        }
        if (!anyBlankInFirst) {
            List<OcrFileSplit> used = new ArrayList<>(splits.subList(0, take));
            List<String> imgs = new ArrayList<>();
            for (OcrFileSplit s : used) {
                imgs.add(s.getImageBase64());
            }
            return new AnalysisImageInputs(used, imgs);
        }
        List<String> pages = expandFullFileToPageBase64s(main);
        if (pages.isEmpty()) {
            log.warn("[Pipeline] 分片缺图且无法从 extra_info 展开，退化为仅包含有图分片: mainId={}", main.getId());
            List<OcrFileSplit> used = splits.stream()
                    .filter(s -> s.getImageBase64() != null && !s.getImageBase64().isBlank())
                    .limit(maxTotalPages)
                    .collect(Collectors.toList());
            List<String> imgs = used.stream().map(OcrFileSplit::getImageBase64).collect(Collectors.toList());
            return new AnalysisImageInputs(used, imgs);
        }
        int n = Math.min(take, pages.size());
        n = Math.min(n, splits.size());
        List<OcrFileSplit> used = new ArrayList<>(n);
        List<String> imgs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            used.add(splits.get(i));
            imgs.add(pages.get(i));
        }
        log.info("[Pipeline] 分片 image_base64 不完整，使用主文件 extra_info 按页展开参与多模态: mainId={} pagesUsed={}",
                main.getId(), n);
        return new AnalysisImageInputs(used, imgs);
    }

    /** 将主文件整份 PDF/图片等解码为每页 JPEG 的纯 base64（与 {@link DocumentImageConverter#toImageDataUrls} 一致）。 */
    private List<String> expandFullFileToPageBase64s(OcrFileMain main) throws Exception {
        String full = readBase64FromExtraInfo(main.getExtraInfo());
        if (full == null || full.isBlank()) {
            return List.of();
        }
        List<String> urls = documentImageConverter.toImageDataUrls(
                main.getFileName(), guessContentType(main.getFileType()), full);
        List<String> out = new ArrayList<>(urls.size());
        for (String u : urls) {
            int comma = u.indexOf(',');
            out.add(comma >= 0 && comma + 1 < u.length() ? u.substring(comma + 1) : u);
        }
        return out;
    }

    private record AnalysisImageInputs(List<OcrFileSplit> usedSplits, List<String> imageBase64s) {}

    private void saveSkippedAnalysis(OcrFileMain main) {
        OcrFileAnalysis a = new OcrFileAnalysis();
        a.setMainId(main.getId());
        a.setBatchNo(main.getBatchNo());
        a.setStatus(OcrFileAnalysis.STATUS_SKIPPED);
        a.setDocType(OcrFileAnalysis.DOC_TYPE_OTHER);
        analysisMapper.insert(a);
    }

    private void saveFailedAnalysis(OcrFileMain main, String errorMessage) {
        OcrFileAnalysis a = new OcrFileAnalysis();
        a.setMainId(main.getId());
        a.setBatchNo(main.getBatchNo());
        a.setStatus(OcrFileAnalysis.STATUS_FAILED);
        a.setDocType(OcrFileAnalysis.DOC_TYPE_OTHER);
        a.setErrorMessage(truncate(errorMessage, 900));
        analysisMapper.insert(a);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // [4] 相似度对比阶段（Python 服务）
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public void triggerComparePhase(String batchNo) {
        OcrPipelineBatch batchPeek = batchMapper.findByBatchNo(batchNo);
        if (batchPeek == null) return;
        PipelineEffectiveConfig eff = pipelineEffectiveConfigResolver.resolve(batchPeek.getExtraInfo());

        if (!eff.compareEnabled()) {
            OcrPipelineBatch b = batchMapper.findByBatchNo(batchNo);
            if (b != null && OcrPipelineBatch.STATUS_ANALYZED.equals(b.getStatus())) {
                markBatchDone(b.getId());
                log.info("[Pipeline] 相似度对比未启用（本批次 pipeline），直接结束批次 batchNo={}", batchNo);
            }
            return;
        }

        OcrPipelineBatch batch = batchMapper.findByBatchNo(batchNo);
        if (batch == null) return;

        int cas = batchMapper.casStatus(batch.getId(),
                OcrPipelineBatch.STATUS_ANALYZED, OcrPipelineBatch.STATUS_COMPARING);
        if (cas == 0) {
            log.info("[Pipeline] 对比阶段已触发或批次状态不满足，跳过 batchNo={}", batchNo);
            return;
        }
        log.info("[Pipeline] 相似度对比阶段开始 batchNo={}", batchNo);

        try {
            List<Long> mainIds = ocrFileDataService.findMainByBatchNo(batchNo).stream()
                    .filter(m -> OcrFileDataService.STATUS_SUCCESS.equals(m.getOcrStatus()))
                    .map(OcrFileMain::getId)
                    .collect(Collectors.toList());

            if (mainIds.size() < 2) {
                log.warn("[Pipeline] OCR 成功文件不足 2 个，无法两两对比，跳过对比阶段 batchNo={} count={}", batchNo, mainIds.size());
                markBatchDone(batch.getId());
                return;
            }

            fileSimilarityService.compareByMainIds(mainIds, batchNo, batch.getAppCode());
            markBatchDone(batch.getId());
            log.info("[Pipeline] 流水线全部完成 batchNo={}", batchNo);
        } catch (Exception e) {
            log.error("[Pipeline] 相似度对比失败 batchNo={}: {}", batchNo, e.getMessage(), e);
            markBatchFailed(batch.getId(), "相似度对比失败: " + truncate(e.getMessage(), 800));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 查询进度
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public BatchProgressView getBatchProgress(String batchNo) {
        OcrPipelineBatch batch = batchMapper.findByBatchNo(batchNo);
        if (batch == null) return null;

        List<OcrFileMain> files = ocrFileDataService.findMainByBatchNo(batchNo);
        List<OcrFileAnalysis> analyses = analysisMapper.findByBatchNo(batchNo);

        int ocrDone = (int) files.stream()
                .filter(f -> OcrFileDataService.STATUS_SUCCESS.equals(f.getOcrStatus()))
                .count();
        int analysisDone = (int) files.stream()
                .filter(f -> OcrFileAnalysis.STATUS_SUCCESS.equals(f.getAnalysisStatus())
                          || OcrFileAnalysis.STATUS_SKIPPED.equals(f.getAnalysisStatus()))
                .count();

        // 转换为轻量 DTO，剔除 ocrResult / prompt / extraInfo / analysisRaw 等 CLOB 大字段，
        // 避免进度轮询接口因响应体过大导致客户端 Broken pipe
        List<FileStatusSummary> fileSummaries = files.stream()
                .map(FileStatusSummary::from)
                .collect(Collectors.toList());
        List<AnalysisSummary> analysisSummaries = analyses.stream()
                .map(AnalysisSummary::from)
                .collect(Collectors.toList());

        List<FileSimilarityResult> simRows = fileSimilarityResultMapper.findByBusinessNo(batchNo);
        List<SimilarityPairSummary> similarityPairs = simRows.stream()
                .map(SimilarityPairSummary::from)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new BatchProgressView(
                BatchSummary.from(batch), fileSummaries, analysisSummaries,
                batch.getTotalFiles(), ocrDone, analysisDone,
                OcrPipelineBatch.STATUS_DONE.equals(batch.getStatus()),
                similarityPairs);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 重试
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public void retryOcrForFile(Long mainId) {
        OcrFileMain main = ocrFileDataService.findMainById(mainId);
        if (main == null) throw new IllegalArgumentException("文件不存在: " + mainId);
        ocrFileDataService.resetForReprocess(mainId);
        try {
            performOcrForFile(main);
        } catch (Exception e) {
            log.error("[Pipeline] 重跑 OCR 失败 mainId={}: {}", mainId, e.getMessage(), e);
            ocrFileDataService.markMainFailed(mainId, truncate(e.getMessage(), 900));
        }
    }

    @Override
    public void retryAnalysisForFile(Long mainId) {
        OcrFileMain main = ocrFileDataService.findMainById(mainId);
        if (main == null) throw new IllegalArgumentException("文件不存在: " + mainId);
        // 删除旧分析记录，重新分析
        analysisMapper.delete(new LambdaQueryWrapper<OcrFileAnalysis>()
                .eq(OcrFileAnalysis::getMainId, mainId));
        try {
            OcrPipelineBatch batch = batchMapper.findByBatchNo(main.getBatchNo());
            PipelineEffectiveConfig eff = batch != null
                    ? pipelineEffectiveConfigResolver.resolve(batch.getExtraInfo())
                    : pipelineEffectiveConfigResolver.resolve(null);
            performAnalysisForFile(main, eff);
        } catch (Exception e) {
            log.error("[Pipeline] 重跑语义分析失败 mainId={}: {}", mainId, e.getMessage(), e);
            ocrFileDataService.markAnalysisFailed(mainId, truncate(e.getMessage(), 900));
            saveFailedAnalysis(main, e.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 内部工具方法
    // ═════════════════════════════════════════════════════════════════════════

    private void markBatchDone(Long batchId) {
        batchMapper.update(new LambdaUpdateWrapper<OcrPipelineBatch>()
                .eq(OcrPipelineBatch::getId, batchId)
                .set(OcrPipelineBatch::getStatus, OcrPipelineBatch.STATUS_DONE));
    }

    private void markBatchFailed(Long batchId, String reason) {
        batchMapper.update(new LambdaUpdateWrapper<OcrPipelineBatch>()
                .eq(OcrPipelineBatch::getId, batchId)
                .set(OcrPipelineBatch::getStatus, OcrPipelineBatch.STATUS_FAILED)
                .set(OcrPipelineBatch::getErrorMessage, truncate(reason, 900)));
        log.error("[Pipeline] 批次失败 batchId={} reason={}", batchId, reason);
    }

    /** 构建分片骨架列表（预建记录，后续逐片回填 OCR 结果） */
    private List<OcrFileSplit> buildSplitSkeletons(OcrFileMain main, int count) {
        List<OcrFileSplit> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            OcrFileSplit s = new OcrFileSplit();
            s.setSplitIndex(i);
            s.setPageNo(i + 1);
            s.setFilePath(main.getFilePath());
            s.setFileType(main.getFileType());
            s.setFileSize(main.getFileSize());
            list.add(s);
        }
        return list;
    }

    /** 从大智部识别响应中提取文本内容 */
    private String extractDazhiText(JsonNode pageNode) {
        if (pageNode == null) return "";
        String content = pageNode.path("content").asText("");
        if (!content.isBlank()) return content;
        JsonNode picList = pageNode.path("response").path("resultMsg").path("picList");
        if (!picList.isArray()) picList = pageNode.path("resultMsg").path("picList");
        if (picList.isArray()) {
            List<String> lines = new ArrayList<>();
            for (JsonNode pic : picList) {
                JsonNode contents = pic.path("picContent").path("contents");
                if (contents.isArray()) {
                    for (JsonNode line : contents) {
                        String t = line.isTextual() ? line.asText("") : line.path("text").asText("");
                        if (t != null && !t.trim().isBlank()) lines.add(t.trim());
                    }
                }
            }
            if (!lines.isEmpty()) return String.join("\n", lines);
        }
        return "";
    }

    /**
     * 将 PipelineFileInfo 的 sha256 和 base64 存入 extra_info JSON。
     * CLOB 字段无大小限制，可存 base64 大文件。
     */
    private String buildExtraInfo(PipelineFileInfo f) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            if (f.sha256() != null)      node.put("sha256", f.sha256());
            if (f.base64Content() != null) node.put("base64", f.base64Content());
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String readBase64FromExtraInfo(String extraInfo) {
        if (extraInfo == null || extraInfo.isBlank()) return null;
        try {
            return objectMapper.readTree(extraInfo).path("base64").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** 从正言正文首个 JSON 块中读取指定字符串字段（用于日志中的 doc_type / doc_type_label） */
    private String extractJsonFieldFromRaw(String rawContent, String fieldName) {
        if (rawContent == null || rawContent.isBlank() || fieldName == null || fieldName.isBlank()) {
            return null;
        }
        String jsonStr = extractFirstJsonBlock(rawContent);
        if (jsonStr == null) {
            return null;
        }
        try {
            String t = objectMapper.readTree(jsonStr).path(fieldName).asText("");
            return t.isBlank() ? null : t;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 从大模型返回文本中提取第一个完整 JSON 对象（{...}） */
    private String extractFirstJsonBlock(String text) {
        if (text == null) return null;
        int start = text.indexOf('{');
        int end   = text.lastIndexOf('}');
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : null;
    }

    private String guessContentType(String fileType) {
        if (fileType == null) return "application/octet-stream";
        return switch (fileType.toLowerCase()) {
            case "pdf"  -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png"  -> "image/png";
            default     -> "application/octet-stream";
        };
    }

    private String nullIfBlank(String s) {
        return (s == null || s.isBlank() || "null".equalsIgnoreCase(s.trim())) ? null : s;
    }

    private String emptyToDefault(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 流水线调试：打印大模型话术/正文/JSON，超长截断并标注总长度 */
    private String logTruncatedForLlm(String s) {
        if (s == null) {
            return "null";
        }
        if (s.length() <= PIPELINE_LLM_LOG_MAX_CHARS) {
            return s;
        }
        return s.substring(0, PIPELINE_LLM_LOG_MAX_CHARS)
                + "...(truncated, totalChars=" + s.length() + ")";
    }

    /**
     * 将 img2text 返回体序列化为 JSON 供日志打印；去掉 batchImageBase64s 等大字段，避免 base64 撑满日志。
     */
    private String safeImg2TextResultJson(ObjectNode result) {
        if (result == null) {
            return "null";
        }
        try {
            ObjectNode copy = result.deepCopy();
            if (copy.has("batchImageBase64s")) {
                copy.put("batchImageBase64s", "[omitted]");
            }
            return objectMapper.writeValueAsString(copy);
        } catch (Exception e) {
            return result.toString();
        }
    }
}
