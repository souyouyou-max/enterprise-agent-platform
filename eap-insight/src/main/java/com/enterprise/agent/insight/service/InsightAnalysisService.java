package com.enterprise.agent.insight.service;

import com.enterprise.agent.common.ai.service.LlmService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 洞察分析服务：将查询结果交给 LLM 生成自然语言分析报告
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightAnalysisService {

    private static final String SYSTEM_PROMPT =
            "你是专业的数据分析师，根据以下数据回答用户问题并给出业务洞察。\n" +
            "要求：\n" +
            "1. 先直接回答问题，再给出 2-3 条深度洞察\n" +
            "2. 如果数据为空，说明未查询到相关数据\n" +
            "3. 在报告末尾用一行注明图表类型建议（格式：【图表建议】bar/line/pie/table）";

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    /**
     * 基于查询结果生成数据分析报告
     *
     * @param question 原始业务问题
     * @param data     SQL 查询结果
     * @return 分析报告（自然语言 + 洞察 + 图表建议）
     */
    public String analyze(String question, List<Map<String, Object>> data) {
        log.info("开始数据洞察分析，数据行数: {}", data.size());

        String dataJson;
        try {
            dataJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (JsonProcessingException e) {
            dataJson = data.toString();
        }

        String userMessage = String.format("数据：\n%s\n\n问题：%s", dataJson, question);
        return llmService.chatWithSystem(SYSTEM_PROMPT, userMessage);
    }

    /**
     * 从分析报告中提取图表建议关键词（bar/line/pie/table）
     */
    public String extractChartHint(String analysis) {
        if (analysis == null) return "table";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("【图表建议】(\\w+)")
                .matcher(analysis);
        return m.find() ? m.group(1).toLowerCase() : "table";
    }
}
