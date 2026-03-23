package com.enterprise.agent.business.pipeline.service.impl;

import com.enterprise.agent.business.pipeline.service.DocumentImageConverter;
import com.enterprise.agent.data.entity.OcrFileAnalysis;
import com.enterprise.agent.data.entity.OcrFileMain;
import com.enterprise.agent.data.entity.OcrFileSplit;
import com.enterprise.agent.data.mapper.OcrFileAnalysisMapper;
import com.enterprise.agent.data.service.OcrFileDataService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * 流水线多模态领域服务：负责
 * 1) 正言请求体构建；2) 分页结果回写 split.llm_result；3) 结构化分析落库。
 * <p>
 * OcrPipelineServiceImpl 仅保留流程编排，避免“大而全”实现类。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineLlmAnalysisDomainService {

    private final ObjectMapper objectMapper;
    private final OcrFileDataService ocrFileDataService;
    private final OcrFileAnalysisMapper analysisMapper;
    private final DocumentImageConverter documentImageConverter;

    @Value("${eap.tools.zhengyan.platform.pipeline-user-id:pipeline-ocr}")
    private String zhengyanUserId;

    @Value("${eap.tools.zhengyan.platform.img2text.max-images-per-call:2}")
    private int maxImagesPerCall;

    public ObjectNode buildZhengyanAnalysisRequest(List<String> imageBase64s, String fileName, String promptText) {
        ObjectNode req = objectMapper.createObjectNode();

        ObjectNode userInfo = objectMapper.createObjectNode();
        userInfo.put("user_id", zhengyanUserId);
        userInfo.put("user_name", zhengyanUserId);
        req.set("user_info", userInfo);
        req.put("text", buildPerPageResultPrompt(promptText, imageBase64s.size()));
        req.put("disableBatch", true);

        ArrayNode attachments = objectMapper.createArrayNode();
        for (int i = 0; i < imageBase64s.size(); i++) {
            String raw = imageBase64s.get(i);
            if (raw.trim().startsWith("[")) {
                try {
                    JsonNode arr = objectMapper.readTree(raw);
                    for (JsonNode item : arr) {
                        attachments.add(buildImageAttachment(fileName, i, item.asText("")));
                    }
                    continue;
                } catch (Exception ignored) {
                    // 非 JSON 数组按普通 base64 处理
                }
            }
            attachments.add(buildImageAttachment(fileName, i, raw));
        }
        req.set("attachments", attachments);
        return req;
    }

    public void persistMultimodalResultsToSplits(
            OcrFileMain main,
            List<OcrFileSplit> usedSplits,
            List<String> requestImageBase64s,
            ObjectNode result,
            String rawContentFallback
    ) {
        if (usedSplits == null || usedSplits.isEmpty()) {
            return;
        }
        Map<Integer, String> perPageTexts = extractPerPageTexts(rawContentFallback, usedSplits.size());
        if (perPageTexts.size() == usedSplits.size()) {
            for (int i = 0; i < usedSplits.size(); i++) {
                String t = perPageTexts.get(i);
                ocrFileDataService.saveSplitLlmResult(usedSplits.get(i).getId(), main.getId(), t == null ? "" : t);
            }
            log.info("[Pipeline] 多模态按 pages 回写 llm_result: mainId={}, splits={}", main.getId(), usedSplits.size());
            return;
        }

        JsonNode batchContentsArr = result.path("batchContents");
        int step = Math.max(1, maxImagesPerCall);
        List<String> batchTexts = new ArrayList<>();
        if (batchContentsArr.isArray()) {
            for (int i = 0; i < batchContentsArr.size(); i++) {
                batchTexts.add(batchContentsArr.get(i).asText(""));
            }
        }
        if (batchTexts.isEmpty()) {
            saveLlmForAllSplits(main.getId(), usedSplits, rawContentFallback);
            log.info("[Pipeline] 多模态按分片回写 llm_result（无 batchContents，全文）: mainId={}, splits={}",
                    main.getId(), usedSplits.size());
            return;
        }

        List<Integer> attachmentToSplit;
        try {
            attachmentToSplit = buildAttachmentToSplitIndex(usedSplits, requestImageBase64s, main.getFileName());
        } catch (Exception e) {
            log.warn("[Pipeline] 附件展开映射失败，按分片写入全文 fallback: mainId={} e={}", main.getId(), e.getMessage());
            attachmentToSplit = fallbackOneAttachmentPerSplit(usedSplits.size());
        }
        if (attachmentToSplit.isEmpty()) {
            saveLlmForAllSplits(main.getId(), usedSplits, rawContentFallback);
            return;
        }

        for (int si = 0; si < usedSplits.size(); si++) {
            String text = mergeBatchTextsForSplit(batchTexts, step, attachmentToSplit, si);
            if (text.isBlank()) {
                text = rawContentFallback;
            }
            ocrFileDataService.saveSplitLlmResult(usedSplits.get(si).getId(), main.getId(), text);
        }
        log.info("[Pipeline] 多模态按分片回写 llm_result: mainId={}, splits={}, batchContents={}, step={}",
                main.getId(), usedSplits.size(), batchTexts.size(), step);
    }

    public OcrFileAnalysis parseAndSaveAnalysis(OcrFileMain main, List<String> rawChunks, String analysisPromptUsed) {
        OcrFileAnalysis a = new OcrFileAnalysis();
        a.setMainId(main.getId());
        a.setBatchNo(main.getBatchNo());
        a.setAnalysisPrompt(analysisPromptUsed);
        String mergedRaw = rawChunks.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .reduce((x, y) -> x + "\n\n--- pipeline img2text chunk ---\n\n" + y)
                .orElse("");
        a.setAnalysisRaw(mergedRaw);
        a.setStatus(OcrFileAnalysis.STATUS_SUCCESS);

        JsonNode node = mergeJsonFromChunks(rawChunks);
        if (node == null || !node.isObject() || node.isEmpty()) {
            node = parseFirstJsonOrEmpty(rawChunks);
        }
        if (node != null && node.isObject() && !node.isEmpty()) {
            try {
                applyAnalysisJsonNode(a, node);
            } catch (Exception e) {
                log.warn("[Pipeline] 语义分析 JSON 解析失败 mainId={}: {} rawContent={}",
                        main.getId(), e.getMessage(), truncate(mergedRaw, 200));
                a.setDocType(OcrFileAnalysis.DOC_TYPE_OTHER);
                a.setDocSummary(truncate(mergedRaw, 500));
            }
        } else {
            log.warn("[Pipeline] 正言未返回可合并的 JSON，存为摘要 mainId={}", main.getId());
            a.setDocType(OcrFileAnalysis.DOC_TYPE_OTHER);
            a.setDocSummary(truncate(mergedRaw, 500));
        }

        analysisMapper.insert(a);
        return a;
    }

    private ObjectNode buildImageAttachment(String fileName, int pageIndex, String base64) {
        ObjectNode att = objectMapper.createObjectNode();
        att.put("name", fileName + "_page" + pageIndex + ".jpg");
        att.put("mimeType", "image/jpeg");
        att.put("base64", base64);
        return att;
    }

    private String buildPerPageResultPrompt(String basePrompt, int totalPages) {
        return basePrompt + """

                【返回格式硬性要求】
                1) 必须只返回一个 JSON 对象，不要 Markdown 代码块。
                2) JSON 顶层必须包含：
                   - overall: 对整份文档的综合抽取结果（字段要求沿用上面的模板）
                   - pages: 按页结果数组，长度必须等于输入页数
                3) pages 每个元素格式：
                   {"page_no": 1, "result": {<该页抽取 JSON>}}
                4) page_no 从 1 开始连续递增，到 %d 结束；每页只包含该页可见内容，禁止跨页合并。
                5) 每页 result 必须显式包含以下字段（用于分片落库后逐页查看）：
                   - page_has_stamp: true|false（该页是否出现公章/印章）
                   - page_stamp_text: "该页可见印章文字，无则 null"
                6) 若该页是报价单/询价单（doc_type=QUOTATION），必须返回表格明细，不可只给汇总：
                   - items: 数组，逐行抽取该页表格；每行至少包含
                     item_name, model_spec, quantity, unit, price, subtotal
                   - total_amount: 页面可见总价（小写金额），没有则 null
                   - summary 可保留，但不能代替 items。
                7) 若页面存在表格但 OCR/视觉不清导致部分字段缺失，仍要保留该行，缺失字段填 null。
                """.formatted(totalPages);
    }

    private void saveLlmForAllSplits(Long mainId, List<OcrFileSplit> splits, String text) {
        for (OcrFileSplit s : splits) {
            ocrFileDataService.saveSplitLlmResult(s.getId(), mainId, text);
        }
    }

    private static List<Integer> fallbackOneAttachmentPerSplit(int splitCount) {
        List<Integer> map = new ArrayList<>(splitCount);
        for (int i = 0; i < splitCount; i++) {
            map.add(i);
        }
        return map;
    }

    private List<Integer> buildAttachmentToSplitIndex(List<OcrFileSplit> usedSplits, List<String> requestImageBase64s,
                                                      String fileName) throws Exception {
        if (requestImageBase64s == null || requestImageBase64s.size() != usedSplits.size()) {
            throw new IllegalArgumentException("requestImageBase64s 与分片数量不一致");
        }
        List<Integer> map = new ArrayList<>();
        for (int i = 0; i < usedSplits.size(); i++) {
            String raw = requestImageBase64s.get(i);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            if (raw.trim().startsWith("[")) {
                JsonNode arr = objectMapper.readTree(raw);
                for (JsonNode item : arr) {
                    String sub = item.asText("");
                    if (sub.isBlank()) {
                        continue;
                    }
                    List<String> urls = documentImageConverter.toImageDataUrls(fileName + "_page" + i + ".jpg", "image/jpeg", sub);
                    for (int u = 0; u < urls.size(); u++) {
                        map.add(i);
                    }
                }
            } else {
                List<String> urls = documentImageConverter.toImageDataUrls(fileName + "_page" + i + ".jpg", "image/jpeg", raw);
                for (int u = 0; u < urls.size(); u++) {
                    map.add(i);
                }
            }
        }
        return map;
    }

    private static String mergeBatchTextsForSplit(List<String> batchTexts, int step,
                                                  List<Integer> attachmentToSplit, int splitIndex) {
        if (batchTexts.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        String last = null;
        for (int a = 0; a < attachmentToSplit.size(); a++) {
            if (attachmentToSplit.get(a) != splitIndex) {
                continue;
            }
            int b = a / step;
            if (b < 0 || b >= batchTexts.size()) {
                continue;
            }
            String t = batchTexts.get(b);
            if (t == null || t.isBlank()) {
                continue;
            }
            if (!t.equals(last)) {
                parts.add(t);
                last = t;
            }
        }
        return String.join("\n\n", parts);
    }

    private JsonNode mergeJsonFromChunks(List<String> rawChunks) {
        if (rawChunks == null || rawChunks.isEmpty()) {
            return objectMapper.createObjectNode();
        }
        JsonNode merged = null;
        for (String raw : rawChunks) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String jsonStr = extractFirstJsonBlock(raw);
            if (jsonStr == null) {
                continue;
            }
            try {
                JsonNode n = objectMapper.readTree(jsonStr);
                if (n.isObject() && n.has("overall") && n.path("overall").isObject()) {
                    n = n.path("overall");
                }
                merged = merged == null ? n : mergeJsonDeep(merged, n);
            } catch (Exception ignored) {
                // 单段非 JSON，跳过
            }
        }
        return merged != null ? merged : objectMapper.createObjectNode();
    }

    private JsonNode parseFirstJsonOrEmpty(List<String> rawChunks) {
        String first = rawChunks.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse("");
        String jsonStr = extractFirstJsonBlock(first);
        if (jsonStr == null) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(jsonStr);
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private Map<Integer, String> extractPerPageTexts(String rawContent, int expectedPages) {
        Map<Integer, String> out = new HashMap<>();
        if (rawContent == null || rawContent.isBlank()) {
            return out;
        }
        String jsonStr = extractFirstJsonBlock(rawContent);
        if (jsonStr == null) {
            return out;
        }
        try {
            JsonNode root = objectMapper.readTree(jsonStr);
            JsonNode pages = root.path("pages");
            if (!pages.isArray()) {
                return out;
            }
            for (JsonNode p : pages) {
                int pageNo = p.path("page_no").asInt(-1);
                if (pageNo < 1 || pageNo > expectedPages) {
                    continue;
                }
                JsonNode pageResult = p.path("result");
                if (pageResult.isMissingNode() || pageResult.isNull()) {
                    pageResult = p;
                }
                out.put(pageNo - 1, objectMapper.writeValueAsString(pageResult));
            }
        } catch (Exception e) {
            log.warn("[Pipeline] pages 解析失败，回退 batch 对齐: {}", e.getMessage());
        }
        return out;
    }

    private void applyAnalysisJsonNode(OcrFileAnalysis a, JsonNode node) {
        a.setDocType(emptyToDefault(node.path("doc_type").asText(""), OcrFileAnalysis.DOC_TYPE_OTHER));
        a.setHasStamp(node.path("has_stamp").asBoolean(false));
        a.setStampText(nullIfBlank(node.path("stamp_text").asText(null)));
        a.setCompanyName(nullIfBlank(node.path("company_name").asText(null)));
        a.setLicenseNo(nullIfBlank(node.path("license_no").asText(null)));

        JsonNode amtNode = node.path("total_amount");
        if (!amtNode.isNull() && !amtNode.isMissingNode()) {
            try {
                a.setTotalAmount(new BigDecimal(amtNode.asText().replaceAll("[^0-9.]", "")));
            } catch (NumberFormatException ignored) {
                // 非数字格式
            }
        }

        JsonNode datesNode = node.path("key_dates");
        if (datesNode.isArray() && datesNode.size() > 0) {
            List<String> dateList = new ArrayList<>();
            for (JsonNode d : datesNode) {
                String ds = d.asText("").trim();
                if (!ds.isBlank()) {
                    dateList.add(ds);
                }
            }
            a.setKeyDates(String.join(",", dateList));
        }
        a.setDocSummary(nullIfBlank(node.path("summary").asText(null)));
        attachStructuredExtra(a, node);
    }

    private static final Set<String> ANALYSIS_JSON_STANDARD_KEYS = Set.of(
            "doc_type", "has_stamp", "stamp_text", "company_name", "license_no",
            "total_amount", "key_dates", "summary");

    private void attachStructuredExtra(OcrFileAnalysis a, JsonNode node) {
        if (node == null || !node.isObject()) {
            return;
        }
        ObjectNode out = objectMapper.createObjectNode();
        node.fields().forEachRemaining(entry -> {
            String k = entry.getKey();
            if (ANALYSIS_JSON_STANDARD_KEYS.contains(k)) {
                return;
            }
            JsonNode v = entry.getValue();
            if ("structured_extra".equals(k) && v != null && v.isObject()) {
                v.fields().forEachRemaining(e -> out.set(e.getKey(), e.getValue()));
            } else {
                out.set(k, v);
            }
        });
        if (out.isEmpty()) {
            return;
        }
        try {
            a.setStructuredExtra(objectMapper.writeValueAsString(out));
        } catch (Exception e) {
            log.warn("[Pipeline] structured_extra 序列化失败 mainId={}: {}", a.getMainId(), e.getMessage());
        }
    }

    private JsonNode mergeJsonDeep(JsonNode a, JsonNode b) {
        if (b == null || b.isNull()) {
            return a;
        }
        if (a == null || a.isNull()) {
            return b;
        }
        if (a.isObject() && b.isObject()) {
            ObjectNode out = (ObjectNode) a.deepCopy();
            b.fields().forEachRemaining(entry -> {
                String k = entry.getKey();
                JsonNode bv = entry.getValue();
                if (!out.has(k)) {
                    out.set(k, bv);
                } else {
                    JsonNode av = out.get(k);
                    if ("total_amount".equals(k) && isNumericNode(av) && isNumericNode(bv)) {
                        out.set(k, maxNumberNode(av, bv));
                    } else {
                        out.set(k, mergeJsonDeep(av, bv));
                    }
                }
            });
            return out;
        }
        if (a.isArray() && b.isArray()) {
            ArrayNode out = objectMapper.createArrayNode();
            for (JsonNode x : a) out.add(x);
            for (JsonNode x : b) out.add(x);
            return out;
        }
        if (a.isTextual() && b.isTextual()) {
            String ta = a.asText();
            String tb = b.asText();
            if (ta.isBlank()) return b;
            if (tb.isBlank()) return a;
            if (ta.equals(tb)) return a;
            return objectMapper.getNodeFactory().textNode(ta + "\n" + tb);
        }
        if (b.isTextual() && !b.asText().isBlank()) {
            return b;
        }
        if (a.isTextual() && !a.asText().isBlank()) {
            return a;
        }
        return b;
    }

    private static boolean isNumericNode(JsonNode n) {
        return n != null && (n.isNumber() || (n.isTextual() && n.asText().matches("-?[0-9]+(\\.[0-9]+)?")));
    }

    private JsonNode maxNumberNode(JsonNode a, JsonNode b) {
        try {
            String sa = a.isNumber() ? a.asText() : a.asText().replaceAll("[^0-9.-]", "");
            String sb = b.isNumber() ? b.asText() : b.asText().replaceAll("[^0-9.-]", "");
            BigDecimal ba = new BigDecimal(sa);
            BigDecimal bb = new BigDecimal(sb);
            return objectMapper.getNodeFactory().numberNode(ba.max(bb));
        } catch (Exception e) {
            return b;
        }
    }

    private String extractFirstJsonBlock(String text) {
        if (text == null) return null;
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return (start >= 0 && end > start) ? text.substring(start, end + 1) : null;
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
}
