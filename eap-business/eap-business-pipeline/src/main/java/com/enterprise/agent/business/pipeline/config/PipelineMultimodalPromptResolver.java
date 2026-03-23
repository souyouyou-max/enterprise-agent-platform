package com.enterprise.agent.business.pipeline.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 流水线多模态（正言 img2text）话术解析：场景 / 任务 → 配置中的模板 key → 实际 prompt 文本。
 *
 * <p>配置路径：{@code eap.pipeline.analysis.multimodal-prompt-key} 与
 * {@code eap.pipeline.prompts.templates.<key>}。未配置模板时回退到 {@link #builtinDefaultPrompt()}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineMultimodalPromptResolver {

    public static final String DEFAULT_KEY = "default";

    private final EapPipelineProperties pipelineProperties;
    private final ObjectMapper objectMapper;

    /**
     * 与历史行为一致的默认结构化抽取话术（JSON 字段与 {@code parseAndSaveAnalysis} 对应）。
     */
    public static String builtinDefaultPrompt() {
        return """
                请分析以下文档图片，提取结构化信息并严格按 JSON 格式返回，不要包含任何解释文字：
                {
                  "doc_type": "BUSINESS_LICENSE|QUOTATION|CONTRACT|INVOICE|SEAL_PAGE|OTHER",
                  "doc_type_label": "与 doc_type 对应的中文证件或文档类型名称，如：营业执照、报价单、合同、发票、印章页等",
                  "has_stamp": true|false,
                  "stamp_text": "公章文字，无则null",
                  "company_name": "公司/单位名称，无则null",
                  "license_no": "营业执照统一社会信用代码18位，无则null",
                  "total_amount": 金额数字（元，无则null）,
                  "key_dates": ["YYYY-MM-DD", ...],
                  "summary": "文档内容摘要，100字以内"
                }
                除上述键外可追加其它键（如证件编号、资质列表），将写入库 structured_extra。
                """;
    }

    /**
     * 按当前全局配置 {@code eap.pipeline.analysis.multimodal-prompt-key} 解析正言 {@code text} 字段内容。
     */
    public String resolveAnalysisPrompt() {
        // 值来自 eap.pipeline.analysis.multimodal-prompt-key（YAML 可用 PipelineEnv.PIPELINE_ANALYSIS_MULTIMODAL_PROMPT_KEY 注入）
        String key = pipelineProperties.getAnalysis().getMultimodalPromptKey();
        if (key == null || key.isBlank()) {
            key = DEFAULT_KEY;
        }
        return resolveByKey(key);
    }

    /**
     * 按指定模板键解析（便于测试或后续按批次覆盖）。
     */
    public String resolveByKey(String key) {
        if (key == null || key.isBlank()) {
            key = DEFAULT_KEY;
        }
        Map<String, String> templates = templatesMap();
        String text = templates.get(key);
        if (text != null && !text.isBlank()) {
            log.debug("[PipelinePrompt] 使用配置模板 key={} len={}", key, text.length());
            return text.trim();
        }
        if (!DEFAULT_KEY.equals(key)) {
            log.warn("[PipelinePrompt] 未找到模板 key={}，回退到内置 default 话术", key);
        }
        return builtinDefaultPrompt();
    }

    /**
     * 将多套模板文案合并为一次 img2text 的 {@code text}；多套时要求模型只输出一个 JSON。
     */
    public String resolveMergedByKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return resolveAnalysisPrompt();
        }
        List<String> normalized = new ArrayList<>();
        for (String k : keys) {
            if (k != null && !k.isBlank()) {
                normalized.add(k.trim());
            }
        }
        if (normalized.isEmpty()) {
            return resolveAnalysisPrompt();
        }
        if (normalized.size() == 1) {
            return resolveByKey(normalized.get(0));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("""
                以下包含多套抽取说明，针对同一批文档图片只需调用一次识别。请综合满足各段要求，只输出**一个** JSON 对象，\
                将各段所需字段合并到同一对象中（字段名相同则以更贴合文档内容的为准；必要时可用数组容纳多条）。\
                输出中须包含 doc_type 与 doc_type_label（中文证件/文档类型），二者须一致。\
                若输入为多页 PDF 的分片，请合并各页可见的表格与金额；若同批混用证件类与报价类模板，请避免遗漏分项报价与总价。\
                不要输出任何解释文字或 Markdown。

                """);
        for (int i = 0; i < normalized.size(); i++) {
            String k = normalized.get(i);
            sb.append("—— 第 ").append(i + 1).append(" 套模板 [").append(k).append("] ——\n");
            sb.append(resolveByKey(k));
            sb.append("\n\n");
        }
        log.info("[PipelinePrompt] 合并 {} 套话术模板 keys={}", normalized.size(), normalized);
        return sb.toString().trim();
    }

    /**
     * 从批次 {@code extra_info} JSON 解析 {@link PipelineEnv#EXTRA_INFO_JSON_MULTIMODAL_PROMPT_KEY}；
     * 无字段或非 JSON 时返回 {@code null}（调用方应回退到全局 {@link #resolveAnalysisPrompt()}）。
     */
    public String parsePromptKeyOverride(String batchExtraInfoJson) {
        if (batchExtraInfoJson == null || batchExtraInfoJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(batchExtraInfoJson);
            JsonNode k = root.path(PipelineEnv.EXTRA_INFO_JSON_MULTIMODAL_PROMPT_KEY);
            if (k.isMissingNode() || k.isNull()) {
                return null;
            }
            String s = k.asText(null);
            return s != null && !s.isBlank() ? s.trim() : null;
        } catch (Exception e) {
            log.debug("[PipelinePrompt] extra_info 非 JSON 或解析失败，忽略 multimodalPromptKey: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> templatesMap() {
        EapPipelineProperties.Prompts p = pipelineProperties.getPrompts();
        if (p == null || p.getTemplates() == null) {
            return Collections.emptyMap();
        }
        return p.getTemplates();
    }
}
