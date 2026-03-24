package com.sinosig.aip.engine.rule.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 简化版余弦相似度：中文 bigram + TF（归一化）
 */
public final class TextCosineSimilarity {

    private TextCosineSimilarity() {}

    public static double cosineBigramTf(String textA, String textB) {
        if (textA == null || textB == null) return 0.0;
        Map<String, Double> a = buildTfVector(textA);
        Map<String, Double> b = buildTfVector(textB);
        return cosine(a, b);
    }

    private static Map<String, Double> buildTfVector(String text) {
        String clean = text.replaceAll("\\s+", "");
        List<String> grams = new ArrayList<>();
        for (int i = 0; i < clean.length() - 1; i++) {
            grams.add(clean.substring(i, i + 2));
        }
        if (grams.isEmpty()) return Map.of();

        Map<String, Integer> tf = new HashMap<>();
        for (String g : grams) {
            tf.merge(g, 1, Integer::sum);
        }
        int total = grams.size();
        Map<String, Double> vec = new HashMap<>();
        for (Map.Entry<String, Integer> e : tf.entrySet()) {
            vec.put(e.getKey(), (double) e.getValue() / total);
        }
        return vec;
    }

    private static double cosine(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        double dot = 0.0;
        for (Map.Entry<String, Double> e : a.entrySet()) {
            dot += e.getValue() * b.getOrDefault(e.getKey(), 0.0);
        }
        double normA = Math.sqrt(a.values().stream().mapToDouble(v -> v * v).sum());
        double normB = Math.sqrt(b.values().stream().mapToDouble(v -> v * v).sum());
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (normA * normB);
    }
}

