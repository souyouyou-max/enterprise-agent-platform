package com.enterprise.agent.business.pipeline.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 将批次 {@code extra_info} JSON 与全局 {@link EapPipelineProperties} 合并为单次执行用的配置。
 *
 * <p>{@code extra_info} 中可<strong>选填</strong>（缺省则用全局 YAML）：
 * {@link PipelineEnv#EXTRA_INFO_JSON_OCR_ENABLED}、{@link PipelineEnv#EXTRA_INFO_JSON_ANALYSIS_ENABLED}、
 * {@link PipelineEnv#EXTRA_INFO_JSON_COMPARE_ENABLED}、{@link PipelineEnv#EXTRA_INFO_JSON_MULTIMODAL_PROMPT_KEY}、
 * {@link PipelineEnv#EXTRA_INFO_JSON_MULTIMODAL_PROMPT_KEYS}、
 * {@link PipelineEnv#EXTRA_INFO_JSON_MAX_IMAGES_PER_FILE}、{@link PipelineEnv#EXTRA_INFO_JSON_MAX_TOTAL_ANALYSIS_PAGES}、
 * {@link PipelineEnv#EXTRA_INFO_JSON_FAIL_TOLERANCE_RATIO}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineEffectiveConfigResolver {

    private final EapPipelineProperties pipelineProperties;
    private final PipelineMultimodalPromptResolver multimodalPromptResolver;
    private final ObjectMapper objectMapper;

    public PipelineEffectiveConfig resolve(String extraInfoJson) {
        JsonNode root = parseRoot(extraInfoJson);

        boolean ocr = mergeBool(optBoolean(root, PipelineEnv.EXTRA_INFO_JSON_OCR_ENABLED),
                pipelineProperties.getOcr().isEnabled());
        boolean analysis = mergeBool(optBoolean(root, PipelineEnv.EXTRA_INFO_JSON_ANALYSIS_ENABLED),
                pipelineProperties.getAnalysis().isEnabled());
        boolean compare = mergeBool(optBoolean(root, PipelineEnv.EXTRA_INFO_JSON_COMPARE_ENABLED),
                pipelineProperties.getCompare().isEnabled());

        Double tolOverride = optDouble(root, PipelineEnv.EXTRA_INFO_JSON_FAIL_TOLERANCE_RATIO);
        double tol = tolOverride != null ? tolOverride : pipelineProperties.getFailToleranceRatio();

        Integer maxBox = optInteger(root, PipelineEnv.EXTRA_INFO_JSON_MAX_IMAGES_PER_FILE);
        int maxImages = maxBox != null ? maxBox : pipelineProperties.getAnalysis().getMaxImagesPerFile();

        Integer maxTotalOpt = optInteger(root, PipelineEnv.EXTRA_INFO_JSON_MAX_TOTAL_ANALYSIS_PAGES);
        int maxTotalPages = maxTotalOpt != null ? maxTotalOpt : pipelineProperties.getAnalysis().getMaxTotalAnalysisPages();

        List<String> templateKeys = resolveMultimodalTemplateKeys(root, extraInfoJson);

        return new PipelineEffectiveConfig(ocr, analysis, compare, tol, maxImages, maxTotalPages, templateKeys);
    }

    private JsonNode parseRoot(String extraInfoJson) {
        if (extraInfoJson == null || extraInfoJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(extraInfoJson);
        } catch (Exception e) {
            log.debug("[Pipeline] extra_info 非 JSON，按全局配置: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    /**
     * 话术模板 key 列表：优先 {@code multimodalPromptKeys} 数组；否则单键 {@code multimodalPromptKey}；
     * 否则全局 {@code eap.pipeline.analysis.multimodal-prompt-key}；再否则 {@code default}。
     */
    private List<String> resolveMultimodalTemplateKeys(JsonNode root, String extraInfoJson) {
        JsonNode arr = root.path(PipelineEnv.EXTRA_INFO_JSON_MULTIMODAL_PROMPT_KEYS);
        if (arr.isArray() && arr.size() > 0) {
            Set<String> seen = new LinkedHashSet<>();
            for (JsonNode n : arr) {
                if (n.isTextual()) {
                    String s = n.asText(null);
                    if (s != null && !s.isBlank()) {
                        seen.add(s.trim());
                    }
                }
            }
            if (!seen.isEmpty()) {
                return List.copyOf(seen);
            }
        }
        String one = multimodalPromptResolver.parsePromptKeyOverride(extraInfoJson);
        if (one != null) {
            return List.of(one);
        }
        String g = pipelineProperties.getAnalysis().getMultimodalPromptKey();
        if (g != null && !g.isBlank()) {
            return List.of(g.trim());
        }
        return List.of(PipelineMultimodalPromptResolver.DEFAULT_KEY);
    }

    private static Boolean optBoolean(JsonNode root, String field) {
        JsonNode n = root.path(field);
        if (n.isMissingNode() || n.isNull()) {
            return null;
        }
        if (n.isBoolean()) {
            return n.booleanValue();
        }
        return null;
    }

    private static Integer optInteger(JsonNode root, String field) {
        JsonNode n = root.path(field);
        if (n.isMissingNode() || n.isNull() || !n.isNumber()) {
            return null;
        }
        return n.intValue();
    }

    private static Double optDouble(JsonNode root, String field) {
        JsonNode n = root.path(field);
        if (n.isMissingNode() || n.isNull() || !n.isNumber()) {
            return null;
        }
        return n.doubleValue();
    }

    private static boolean mergeBool(Boolean override, boolean global) {
        return override != null ? override : global;
    }
}
