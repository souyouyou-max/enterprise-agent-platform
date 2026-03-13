package com.enterprise.agent.common.ai.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LlmResponse {

    /** LLM 输出的文本内容 */
    private String content;

    /** 使用的模型 */
    private String model;

    /** 输入 token 数 */
    private int inputTokens;

    /** 输出 token 数 */
    private int outputTokens;

    /** 是否来自缓存 */
    private boolean cached;

    /** 完成原因（stop / length / tool_calls 等） */
    private String finishReason;
}
