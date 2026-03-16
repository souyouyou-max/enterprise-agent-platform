package com.enterprise.agent.engine.rule.analyzer;

import com.enterprise.agent.common.ai.service.LlmService;
import com.enterprise.agent.data.entity.ProcurementBid;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 投标文件相似度分析器（围标维度2）
 * TF-IDF余弦相似度粗筛（阈值0.6），命中后调LLM精细分析
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BidSimilarityAnalyzer {

    private final LlmService llmService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final double COSINE_THRESHOLD = 0.6;
    private static final int MAX_TEXT_LENGTH = 3000;
    private static final int HALF_LENGTH = 1500;

    /**
     * 分析一批投标记录，返回疑似相似的投标对
     */
    public List<SimilarBidPair> analyze(List<ProcurementBid> bids) {
        List<SimilarBidPair> results = new ArrayList<>();

        // 过滤掉无文本的投标
        List<ProcurementBid> withText = bids.stream()
                .filter(b -> b.getBidDocumentText() != null && !b.getBidDocumentText().isBlank())
                .toList();

        if (withText.size() < 2) {
            return results;
        }

        // 两两比较
        for (int i = 0; i < withText.size(); i++) {
            for (int j = i + 1; j < withText.size(); j++) {
                ProcurementBid bidA = withText.get(i);
                ProcurementBid bidB = withText.get(j);

                double cosine = cosineSimilarity(bidA.getBidDocumentText(), bidB.getBidDocumentText());
                log.debug("[BidSimilarityAnalyzer] 余弦相似度：{}({}) vs {}({}) = {}",
                        bidA.getSupplierName(), bidA.getSupplierId(),
                        bidB.getSupplierName(), bidB.getSupplierId(), cosine);

                if (cosine >= COSINE_THRESHOLD) {
                    log.info("[BidSimilarityAnalyzer] 粗筛命中（cosine={}），调用LLM精细分析", cosine);
                    LlmSimilarityResult llmResult = callLlmAnalysis(bidA, bidB);

                    SimilarBidPair pair = new SimilarBidPair();
                    pair.setBidA(bidA);
                    pair.setBidB(bidB);
                    pair.setCosineSimilarity(cosine);
                    pair.setLlmResult(llmResult);
                    results.add(pair);
                }
            }
        }

        return results;
    }

    // ---- TF-IDF余弦相似度（中文bigram分词，n=2）----

    private double cosineSimilarity(String textA, String textB) {
        Map<String, Double> tfidfA = buildTfIdfVector(textA);
        Map<String, Double> tfidfB = buildTfIdfVector(textB);
        return cosine(tfidfA, tfidfB);
    }

    private Map<String, Double> buildTfIdfVector(String text) {
        Map<String, Integer> tf = new HashMap<>();
        List<String> bigrams = bigram(text);
        for (String gram : bigrams) {
            tf.merge(gram, 1, Integer::sum);
        }
        // 简化TF-IDF：直接用TF（归一化）
        int total = bigrams.size();
        Map<String, Double> vec = new HashMap<>();
        for (Map.Entry<String, Integer> e : tf.entrySet()) {
            vec.put(e.getKey(), (double) e.getValue() / total);
        }
        return vec;
    }

    private List<String> bigram(String text) {
        // 去除空白后做bigram
        String clean = text.replaceAll("\\s+", "");
        List<String> grams = new ArrayList<>();
        for (int i = 0; i < clean.length() - 1; i++) {
            grams.add(clean.substring(i, i + 2));
        }
        return grams;
    }

    private double cosine(Map<String, Double> a, Map<String, Double> b) {
        double dot = 0.0;
        for (Map.Entry<String, Double> e : a.entrySet()) {
            dot += e.getValue() * b.getOrDefault(e.getKey(), 0.0);
        }
        double normA = Math.sqrt(a.values().stream().mapToDouble(v -> v * v).sum());
        double normB = Math.sqrt(b.values().stream().mapToDouble(v -> v * v).sum());
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (normA * normB);
    }

    // ---- LLM精细分析 ----

    private LlmSimilarityResult callLlmAnalysis(ProcurementBid bidA, ProcurementBid bidB) {
        String textA = truncate(bidA.getBidDocumentText());
        String textB = truncate(bidB.getBidDocumentText());

        String prompt = String.format("""
                请分析以下两份投标文件的相似度，判断是否存在围标串标嫌疑。

                【投标方A】%s（%s）
                文件内容：
                %s

                【投标方B】%s（%s）
                文件内容：
                %s

                请以JSON格式输出分析结果，字段如下：
                - similarityScore: 相似度评分（0-100的整数）
                - suspiciousLevel: 疑似程度（HIGH/MEDIUM/LOW/NONE）
                - evidences: 证据列表（字符串数组，列出具体相似之处）
                - conclusion: 综合结论（一段话）

                只输出JSON，不要有其他内容。
                """,
                bidA.getSupplierName(), bidA.getSupplierId(), textA,
                bidB.getSupplierName(), bidB.getSupplierId(), textB);

        try {
            String response = llmService.simpleChat(prompt);
            // 提取JSON部分
            String json = extractJson(response);
            return objectMapper.readValue(json, LlmSimilarityResult.class);
        } catch (Exception e) {
            log.warn("[BidSimilarityAnalyzer] LLM分析失败，使用默认结果：{}", e.getMessage());
            LlmSimilarityResult fallback = new LlmSimilarityResult();
            fallback.setSimilarityScore(70);
            fallback.setSuspiciousLevel("MEDIUM");
            fallback.setEvidences(List.of("TF-IDF余弦相似度超过阈值，LLM分析失败"));
            fallback.setConclusion("基于文本相似度粗筛，疑似存在围标，建议人工复核。");
            return fallback;
        }
    }

    private String truncate(String text) {
        if (text.length() <= MAX_TEXT_LENGTH) return text;
        return text.substring(0, HALF_LENGTH) + "\n...[省略中间部分]...\n"
                + text.substring(text.length() - HALF_LENGTH);
    }

    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    // ---- 内部类 ----

    @Data
    public static class SimilarBidPair {
        private ProcurementBid bidA;
        private ProcurementBid bidB;
        private double cosineSimilarity;
        private LlmSimilarityResult llmResult;
    }

    @Data
    public static class LlmSimilarityResult {
        private int similarityScore;
        private String suspiciousLevel;
        private List<String> evidences;
        private String conclusion;
    }
}
