package com.sinosig.aip.engine.rag.knowledge.service;

import com.sinosig.aip.common.ai.service.EmbeddingService;
import com.sinosig.aip.engine.rag.knowledge.entity.KnowledgeDocument;
import com.sinosig.aip.engine.rag.knowledge.repository.KnowledgeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 知识文档索引服务
 * - 向量化并持久化文档
 * - 余弦相似度内存检索（生产可替换为 Milvus / PGVector）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeIndexService {

    private final KnowledgeRepository knowledgeRepository;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    /**
     * 录入并索引一篇文档：向量化 → 序列化 → 持久化
     *
     * @param title    文档标题
     * @param content  文档内容
     * @param category 文档分类
     * @return 已保存的文档实体
     */
    public KnowledgeDocument indexDocument(String title, String content, String category) {
        log.info("开始索引文档: title={}, category={}", title, category);

        // 向量化（title + content 拼接，提升检索质量）
        String textToEmbed = title + "\n" + content;
        float[] vector = embeddingService.embedSingle(textToEmbed);

        // 序列化向量为 JSON 字符串
        String embeddingJson;
        try {
            embeddingJson = objectMapper.writeValueAsString(toDoubleList(vector));
        } catch (Exception e) {
            throw new RuntimeException("向量序列化失败", e);
        }

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTitle(title);
        doc.setContent(content);
        doc.setCategory(category);
        doc.setEmbedding(embeddingJson);
        doc.setCreatedAt(LocalDateTime.now());

        knowledgeRepository.insert(doc);
        log.info("文档索引完成: id={}", doc.getId());
        return doc;
    }

    /**
     * 语义相似度检索：对问题向量化后与库中所有文档做余弦相似度排序，返回 topK
     *
     * @param query 用户查询
     * @param topK  返回数量
     * @return 相似度最高的 K 篇文档
     */
    public List<KnowledgeDocument> searchSimilar(String query, int topK) {
        log.debug("开始语义检索: query={}, topK={}", query, topK);

        float[] queryVector = embeddingService.embedSingle(query);
        List<KnowledgeDocument> allDocs = knowledgeRepository.selectList(null);

        record ScoredDoc(KnowledgeDocument doc, double score) {}

        List<ScoredDoc> scored = new ArrayList<>();
        for (KnowledgeDocument doc : allDocs) {
            if (doc.getEmbedding() == null || doc.getEmbedding().isBlank()) {
                continue;
            }
            try {
                List<Double> embList = objectMapper.readValue(doc.getEmbedding(),
                        new TypeReference<>() {});
                float[] docVector = toFloatArray(embList);
                double similarity = cosineSimilarity(queryVector, docVector);
                scored.add(new ScoredDoc(doc, similarity));
            } catch (Exception e) {
                log.warn("文档 {} 向量解析失败，跳过", doc.getId());
            }
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(ScoredDoc::score).reversed())
                .limit(topK)
                .map(ScoredDoc::doc)
                .toList();
    }

    /**
     * 查询全部文档
     */
    public List<KnowledgeDocument> listAll() {
        return knowledgeRepository.selectList(null);
    }

    // ────────── 工具方法 ──────────

    private double cosineSimilarity(float[] a, float[] b) {
        int len = Math.min(a.length, b.length);
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < len; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }

    private List<Double> toDoubleList(float[] arr) {
        List<Double> list = new ArrayList<>(arr.length);
        for (float v : arr) list.add((double) v);
        return list;
    }

    private float[] toFloatArray(List<Double> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i).floatValue();
        return arr;
    }
}
