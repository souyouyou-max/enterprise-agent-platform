package com.enterprise.agent.common.ai.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class LlmRequest {

    /** 系统提示词 */
    private String systemPrompt;

    /** 用户消息 */
    private String userMessage;

    /** 历史对话（可选） */
    private List<ChatMessage> history;

    /** 模型名称（可选，不填使用默认） */
    private String model;

    /** 温度（0.0 ~ 1.0） */
    @Builder.Default
    private double temperature = 0.7;

    /** 最大 token 数 */
    @Builder.Default
    private int maxTokens = 4096;

    /** 额外参数 */
    private Map<String, Object> extraParams;

    @Data
    @Builder
    public static class ChatMessage {
        private String role;  // system / user / assistant
        private String content;
    }
}
