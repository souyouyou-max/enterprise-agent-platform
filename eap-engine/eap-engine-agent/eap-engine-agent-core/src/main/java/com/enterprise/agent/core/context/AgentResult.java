package com.enterprise.agent.core.context;

import com.enterprise.agent.common.core.enums.AgentRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent 执行结果
 */
@Data
@Builder
public class AgentResult {

    /** 执行的 Agent 类型 */
    private AgentRole agentRole;

    /** 主要输出内容 */
    private String output;

    /** 子任务执行结果（ExecutorAgent 使用） */
    private Map<Integer, String> subTaskResults;

    /** 质量评分（ReviewerAgent 使用，0-100） */
    private Integer qualityScore;

    /** 问题列表（ReviewerAgent 使用） */
    private List<String> issues;

    /** 是否成功 */
    @Builder.Default
    private boolean success = true;

    /** 错误信息 */
    private String errorMessage;

    /** 执行时间戳 */
    @Builder.Default
    private LocalDateTime executedAt = LocalDateTime.now();

    /** 重试次数 */
    @Builder.Default
    private int retryCount = 0;

    public static AgentResult failure(AgentRole role, String errorMessage) {
        return AgentResult.builder()
                .agentRole(role)
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
