package com.enterprise.agent.engine.rule.client.dto;

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
        private Map<String, Object> result;
    }
}

