package com.enterprise.agent.impl.reviewer.toolkit;

import com.enterprise.agent.common.ai.service.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * ReviewerToolkit - 审查 Agent 专属技能集
 * <p>
 * 提供内容评分、完整性检查、幻觉检测、改进建议四项质量保障能力。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewerToolkit {

    private final LlmService llmService;

    /**
     * 对内容按指定标准评分（0-100分）
     */
    @Tool(description = "按指定评分标准对内容进行0-100分评分，返回JSON：" +
            "{\"score\":85,\"breakdown\":{\"completeness\":30,\"accuracy\":28,\"usability\":16,\"compliance\":11},\"summary\":\"评分说明\"}")
    public String scoreOutput(String content, String criteria) {
        log.info("[ReviewerToolkit] scoreOutput");
        String prompt = String.format("""
                请按照以下标准对内容进行评分（总分100分）：

                评分标准：
                %s

                待评分内容：
                %s

                严格输出JSON格式（不含其他文字）：
                {
                  "score": 总分数字,
                  "breakdown": {
                    "completeness": 完整性得分(满分30),
                    "accuracy": 准确性得分(满分30),
                    "usability": 可用性得分(满分20),
                    "compliance": 规范性得分(满分20)
                  },
                  "summary": "评分综合说明"
                }
                """, criteria, content);
        return llmService.simpleChat(prompt);
    }

    /**
     * 检查内容是否满足需求，返回缺失项列表
     */
    @Tool(description = "检查内容是否满足指定需求，返回JSON：" +
            "{\"satisfied\":true/false,\"missingItems\":[\"缺失项1\",\"缺失项2\"],\"coverageRate\":\"85%\"}")
    public String checkCompleteness(String content, String requirements) {
        log.info("[ReviewerToolkit] checkCompleteness");
        String prompt = String.format("""
                请检查以下内容是否满足所有需求要求：

                需求要求：
                %s

                实际内容：
                %s

                严格输出JSON格式（不含其他文字）：
                {
                  "satisfied": true或false,
                  "missingItems": ["缺失项1", "缺失项2"],
                  "coverageRate": "覆盖率百分比"
                }
                """, requirements, content);
        return llmService.simpleChat(prompt);
    }

    /**
     * 检测内容中的明显幻觉或自相矛盾之处
     */
    @Tool(description = "检测内容中的AI幻觉（无中生有的信息）或逻辑自相矛盾之处，返回JSON：" +
            "{\"hasIssues\":true/false,\"issues\":[{\"type\":\"hallucination|contradiction\",\"location\":\"位置\",\"description\":\"说明\"}]}")
    public String detectHallucination(String content) {
        log.info("[ReviewerToolkit] detectHallucination");
        String prompt = String.format("""
                请仔细检查以下内容中是否存在AI幻觉（无中生有的数据或事实）或逻辑自相矛盾之处：

                待检测内容：
                %s

                检测重点：
                1. 是否引用了不存在的数据、人名、时间等
                2. 前后数据是否自相矛盾
                3. 结论与数据是否一致

                严格输出JSON格式（不含其他文字）：
                {
                  "hasIssues": true或false,
                  "issues": [
                    {
                      "type": "hallucination或contradiction",
                      "location": "问题所在位置描述",
                      "description": "问题详细说明"
                    }
                  ]
                }
                """, content);
        return llmService.simpleChat(prompt);
    }

    /**
     * 给出内容的具体改进建议
     */
    @Tool(description = "分析内容不足之处并给出具体可操作的改进建议，返回JSON：" +
            "{\"suggestions\":[{\"priority\":\"HIGH|MEDIUM|LOW\",\"aspect\":\"改进方面\",\"suggestion\":\"具体建议\"}]}")
    public String suggestImprovement(String content) {
        log.info("[ReviewerToolkit] suggestImprovement");
        String prompt = String.format("""
                请分析以下内容的不足之处，给出具体可操作的改进建议：

                内容：
                %s

                从以下方面分析：完整性、准确性、清晰度、专业性、实用性

                严格输出JSON格式（不含其他文字）：
                {
                  "suggestions": [
                    {
                      "priority": "HIGH|MEDIUM|LOW",
                      "aspect": "改进方面",
                      "suggestion": "具体改进建议"
                    }
                  ]
                }
                """, content);
        return llmService.simpleChat(prompt);
    }
}
