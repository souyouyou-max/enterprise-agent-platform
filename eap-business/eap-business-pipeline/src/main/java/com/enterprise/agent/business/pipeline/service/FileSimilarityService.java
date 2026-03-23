package com.enterprise.agent.business.pipeline.service;

import com.enterprise.agent.data.entity.FileSimilarityResult;
import com.enterprise.agent.data.entity.OcrFileMain;
import com.enterprise.agent.data.entity.OcrFileSplit;
import com.enterprise.agent.data.mapper.FileSimilarityResultMapper;
import com.enterprise.agent.data.service.OcrFileDataService;
import com.enterprise.agent.engine.rule.client.BidAnalysisClient;
import com.enterprise.agent.engine.rule.client.dto.BidAnalysisBase64File;
import com.enterprise.agent.engine.rule.client.dto.BidAnalysisCompareBase64Request;
import com.enterprise.agent.engine.rule.client.dto.BidAnalysisCompareBase64Response;
import com.enterprise.agent.engine.rule.client.dto.BidAnalysisCompareBase64Response.FileComparison;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 多附件文件相似度对比服务
 *
 * <h3>对话多附件相似度对比流程</h3>
 * <pre>
 * 前端上传多附件
 *     ↓
 * 各文件已经 OCR 处理完毕，记录在 ocr_file_main + ocr_file_split
 *     ↓
 * compareByMainIds()
 *     ├── 文字相似度：读取 ocr_file_split.ocr_result（分段识别文字）→ 拼接 → 传 Python 对比
 *     └── 文件整体相似度：由 Python compare-base64 接口中的 fileSimilarity 字段返回
 *         （SHA-256 精确匹配 + PDF/图片感知哈希均值）
 *     ↓
 * 结果写入 file_similarity_result 表（入库）
 *     ↓
 * 返回 FileSimilarityResult 列表
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileSimilarityService {

    private final OcrFileDataService ocrFileDataService;
    private final BidAnalysisClient bidAnalysisClient;
    private final FileSimilarityResultMapper fileSimilarityResultMapper;
    private final ObjectMapper objectMapper;

    /**
     * 根据已完成 OCR 的文件主表 ID 列表，执行两两相似度对比并入库。
     *
     * @param mainIds    {@code ocr_file_main.id} 列表，至少 2 个
     * @param businessNo 本次对比的业务流水号（用于入库标识，建议与对话 ID 绑定）
     * @param appCode    应用编码（可选）
     * @return 本次对比写入的 FileSimilarityResult 列表
     */
    @Transactional
    public List<FileSimilarityResult> compareByMainIds(List<Long> mainIds, String businessNo, String appCode) {
        if (mainIds == null || mainIds.size() < 2) {
            throw new IllegalArgumentException("至少需要 2 个文件主表 ID 才能进行相似度对比");
        }

        // 1. 加载各文件主记录 & 分段 OCR 文字
        List<OcrFileMain> mains = mainIds.stream()
                .map(ocrFileDataService::findMainById)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (mains.size() < 2) {
            throw new IllegalStateException("有效的 ocr_file_main 记录不足 2 条，无法对比");
        }

        // 2. 为每个文件拼接分段文字（text similarity 数据源：ocr_file_split.ocr_result）
        //    同时收集文件元信息，构造 compare-base64 请求（实际不发送文件字节，只发送已有OCR文本）
        //    → 改用 /analyze/compare-texts 接口（传已提取文本，避免重复解析）
        List<String> texts = new ArrayList<>();
        for (OcrFileMain main : mains) {
            String fullText = buildTextFromSplits(main.getId(), main.getOcrResult());
            texts.add(fullText);
            log.info("[FileSimilarity] mainId={} fileName={} textLen={}", main.getId(), main.getFileName(),
                    fullText == null ? 0 : fullText.replaceAll("\\s+", "").length());
        }

        // 3. 调用 Python /analyze/compare-texts（传已有文字，避免重复 OCR）
        //    同时调用 /analyze/compare-base64 获取 fileSimilarity（需要文件 base64，若无则用 sha256 仅做精确匹配）
        //    —— 此处优先用 compare-texts（文字已在库中），fileSimilarity 从 ocr_file_main 元信息中推断
        BidAnalysisCompareBase64Response pythonResp = callPythonCompareTexts(mains, texts);

        // 4. 逐对写入 file_similarity_result
        List<FileSimilarityResult> saved = new ArrayList<>();
        if (pythonResp != null && pythonResp.getComparisons() != null) {
            for (FileComparison comp : pythonResp.getComparisons()) {
                OcrFileMain mainA = findByName(mains, comp.getA());
                OcrFileMain mainB = findByName(mains, comp.getB());
                FileSimilarityResult record = buildRecord(businessNo, appCode, mainA, mainB, comp);
                fileSimilarityResultMapper.insert(record);
                saved.add(record);
                log.info("[FileSimilarity] 入库 id={} a={} b={} tfidf={} exactMatch={} riskLevel={}",
                        record.getId(), comp.getA(), comp.getB(),
                        record.getTextTfidfCosine(), record.getFileExactMatch(), record.getRiskLevel());
            }
        }
        return saved;
    }

    // ── 私有方法 ──────────────────────────────────────────────────────────

    /**
     * 拼接 ocr_file_split 中所有分段识别文字，作为文字相似度对比的输入。
     * 如果分片表无数据，则回退到 ocr_file_main.ocr_result（汇总文字）。
     */
    private String buildTextFromSplits(Long mainId, String fallbackOcrResult) {
        try {
            List<OcrFileSplit> splits = ocrFileDataService.findSplitsByMainId(mainId);
            if (splits != null && !splits.isEmpty()) {
                // 按 split_index 排序后拼接
                return splits.stream()
                        .filter(s -> s.getOcrResult() != null && !s.getOcrResult().isBlank())
                        .sorted(Comparator.comparingInt(s -> s.getSplitIndex() == null ? 0 : s.getSplitIndex()))
                        .map(OcrFileSplit::getOcrResult)
                        .collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            log.warn("[FileSimilarity] 读取分片文字失败 mainId={}: {}", mainId, e.getMessage());
        }
        return fallbackOcrResult == null ? "" : fallbackOcrResult;
    }

    /**
     * 使用 Python compare-base64 接口：将文件名 + 已提取文字模拟为"纯文本文件"
     * 以 UTF-8 base64 编码传入，Python 会走 _bytes_to_text 路径直接解析文本，
     * 返回文字相似度 result；fileSimilarity 字段则根据 sha256 进行精确匹配推断。
     */
    private BidAnalysisCompareBase64Response callPythonCompareTexts(List<OcrFileMain> mains, List<String> texts) {
        try {
            List<BidAnalysisBase64File> files = new ArrayList<>();
            for (int i = 0; i < mains.size(); i++) {
                OcrFileMain main = mains.get(i);
                String text = texts.get(i);
                if (text == null) text = "";

                BidAnalysisBase64File f = new BidAnalysisBase64File();
                // 文件名用 .txt 后缀，让 Python 走文本解析路径（_bytes_to_text）
                f.setFilename(stripExtension(main.getFileName()) + "__" + main.getId() + ".txt");
                f.setContent_b64(Base64.getEncoder().encodeToString(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                files.add(f);
            }

            BidAnalysisCompareBase64Request req = new BidAnalysisCompareBase64Request();
            req.setFiles(files);

            log.info("[FileSimilarity] 调用 Python compare-base64 files={}", files.size());
            return bidAnalysisClient.compareBase64(req);
        } catch (Exception e) {
            log.error("[FileSimilarity] 调用 Python 服务失败: {}", e.getMessage(), e);
            return null;
        }
    }

    private OcrFileMain findByName(List<OcrFileMain> mains, String nameFromPython) {
        if (nameFromPython == null) return null;
        return mains.stream()
                .filter(m -> nameFromPython.contains(String.valueOf(m.getId())))
                .findFirst()
                .orElse(null);
    }

    private FileSimilarityResult buildRecord(
            String businessNo, String appCode,
            OcrFileMain mainA, OcrFileMain mainB,
            FileComparison comp) {

        FileSimilarityResult r = new FileSimilarityResult();
        r.setBusinessNo(businessNo);
        r.setAppCode(appCode);

        // 文件标识
        if (mainA != null) {
            r.setFileAMainId(mainA.getId());
            r.setFileAName(mainA.getFileName());
            r.setFileASha256(extractSha256FromMeta(mainA));
        } else {
            r.setFileAName(comp.getA());
        }
        if (mainB != null) {
            r.setFileBMainId(mainB.getId());
            r.setFileBName(mainB.getFileName());
            r.setFileBSha256(extractSha256FromMeta(mainB));
        } else {
            r.setFileBName(comp.getB());
        }

        // 文字相似度
        Map<String, Object> res = comp.getResult();
        if (res != null && Boolean.TRUE.equals(res.get("ok"))) {
            r.setTextTfidfCosine(toBigDecimal(res.get("tfidfCosine")));
            r.setTextDifflibRatio(toBigDecimal(res.get("difflibRatio")));
            r.setTextLongestCommon(toInt(res.get("longestCommonRunChars")));
            r.setTextSegments50(toInt(res.get("matchingSegments50+")));
            r.setTextBlocks500(toInt(res.get("commonBlocksCount500+")));
            r.setTextLenA(toInt(res.get("lenA")));
            r.setTextLenB(toInt(res.get("lenB")));
        }

        // 文件整体相似度
        Map<String, Object> fileSim = comp.getFileSimilarity();
        if (fileSim != null && Boolean.TRUE.equals(fileSim.get("ok"))) {
            r.setFileExactMatch(Boolean.TRUE.equals(fileSim.get("exactMatch")));
            Object visualSim = fileSim.get("visualSim");
            if (visualSim != null) {
                r.setFileVisualSim(toBigDecimal(visualSim));
            }
            // 补充 SHA-256（若主记录未提供）
            if (r.getFileASha256() == null) r.setFileASha256(asString(fileSim.get("sha256A")));
            if (r.getFileBSha256() == null) r.setFileBSha256(asString(fileSim.get("sha256B")));
        }

        // 汇总风险（来自 Python summary 或者自行计算）
        r.setRiskLevel(inferRiskLevel(r));
        r.setRiskLabel(inferRiskLabel(r.getRiskLevel()));

        // 扩展明细 JSON
        try {
            r.setExtraDetail(objectMapper.writeValueAsString(comp));
        } catch (Exception e) {
            log.warn("[FileSimilarity] extraDetail 序列化失败: {}", e.getMessage());
        }

        return r;
    }

    // ── 辅助方法 ──────────────────────────────────────────────────────────

    private String stripExtension(String fileName) {
        if (fileName == null) return "file";
        int idx = fileName.lastIndexOf('.');
        return idx > 0 ? fileName.substring(0, idx) : fileName;
    }

    private String extractSha256FromMeta(OcrFileMain main) {
        // sha256 可存储在 extra_info JSON 中，格式 {"sha256":"..."}
        if (main.getExtraInfo() == null) return null;
        try {
            Map<?, ?> map = objectMapper.readValue(main.getExtraInfo(), Map.class);
            Object v = map.get("sha256");
            return v == null ? null : v.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String inferRiskLevel(FileSimilarityResult r) {
        if (Boolean.TRUE.equals(r.getFileExactMatch())) return "high";
        double tfidf = r.getTextTfidfCosine() == null ? 0 : r.getTextTfidfCosine().doubleValue();
        double ratio = r.getTextDifflibRatio() == null ? 0 : r.getTextDifflibRatio().doubleValue();
        int longest = r.getTextLongestCommon() == null ? 0 : r.getTextLongestCommon();
        double score = tfidf * 0.5 + ratio * 0.3 + (longest > 500 ? 0.2 : longest > 100 ? 0.1 : 0);
        if (score > 0.85) return "high";
        if (score > 0.5)  return "medium";
        return "low";
    }

    private String inferRiskLabel(String level) {
        return switch (level == null ? "" : level) {
            case "high"   -> "高风险 · 文件高度相似";
            case "medium" -> "中风险 · 建议人工复核";
            default       -> "低风险 · 未发现明显相似";
        };
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return null;
        try { return new BigDecimal(v.toString()); } catch (Exception e) { return null; }
    }

    private int toInt(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; }
    }

    private String asString(Object v) {
        return v == null ? null : v.toString();
    }
}
