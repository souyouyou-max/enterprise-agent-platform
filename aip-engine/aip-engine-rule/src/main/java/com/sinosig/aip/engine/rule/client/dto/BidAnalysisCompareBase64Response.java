package com.sinosig.aip.engine.rule.client.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class BidAnalysisCompareBase64Response {
    private List<String> files;
    private List<Map<String, Object>> fileMetas;
    private List<FileComparison> comparisons;

    @Data
    public static class FileComparison {
        private String a;
        private String b;
        /** 文字相似度（分段OCR结果对比，TF-IDF余弦 + difflib） */
        private Map<String, Object> result;
        /** 视觉相似度（PDF分页感知哈希对比） */
        private Map<String, Object> visualSimilarity;
        /** 文件整体相似度（SHA-256精确匹配 + 感知哈希均值） */
        private Map<String, Object> fileSimilarity;
    }
}

