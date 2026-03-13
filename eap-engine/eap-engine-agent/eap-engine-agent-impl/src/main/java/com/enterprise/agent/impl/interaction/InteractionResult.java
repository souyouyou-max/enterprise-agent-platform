package com.enterprise.agent.impl.interaction;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * InteractionResult - AI 交互中心单轮对话的返回结果
 */
@Data
@Builder
public class InteractionResult {

    /** 会话 ID */
    private String sessionId;

    /** 用户原始消息 */
    private String userMessage;

    /** 路由到的 Agent 类型 */
    private AgentType agentType;

    /** AI 回复内容 */
    private String response;

    /** 本轮调用的工具列表 */
    private List<String> usedTools;

    /** 响应时间戳 */
    private LocalDateTime timestamp;

    /**
     * 路由目标 Agent 类型枚举
     */
    public enum AgentType {
        PLANNER("任务规划Pipeline - Planner→Executor→Reviewer→Communicator"),
        KNOWLEDGE("企业知识问答 - RAG检索增强"),
        INSIGHT("数据洞察分析 - NL2BI三步流水线"),
        GENERAL("直接回答 - LLM通用对话");

        private final String description;

        AgentType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
