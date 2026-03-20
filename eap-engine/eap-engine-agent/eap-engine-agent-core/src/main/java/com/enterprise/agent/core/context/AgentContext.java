package com.enterprise.agent.core.context;

import com.enterprise.agent.common.core.enums.ReportStyle;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 任务上下文 - 贯穿整个 Pipeline 的数据容器
 */
@Data
@Builder
public class AgentContext {

    /** 数据库任务 ID */
    private String taskId;

    /** 用户目标 */
    private String goal;

    /** 任务名称 */
    private String taskName;

    /** Planner 拆解的子任务列表 */
    @Builder.Default
    private List<SubTask> subTasks = new ArrayList<>();

    /** Executor 执行结果 Map（subTaskId -> result） */
    @Builder.Default
    private Map<Integer, String> executionResults = new HashMap<>();

    /** Reviewer 质量评分（0-100） */
    private Integer reviewScore;

    /** Reviewer 发现的问题列表 */
    @Builder.Default
    private List<String> reviewIssues = new ArrayList<>();

    /** 是否通过审查 */
    @Builder.Default
    private boolean reviewPassed = false;

    /** 最终报告 */
    private String finalReport;

    /** 报告风格 */
    @Builder.Default
    private ReportStyle reportStyle = ReportStyle.DETAILED;

    /** 对话历史（用于多轮记忆） */
    @Builder.Default
    private List<String> conversationHistory = new ArrayList<>();

    /** 扩展元数据 */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /** 子任务执行状态枚举，取代裸字符串，避免拼写错误 */
    public enum SubTaskStatus {
        PENDING, EXECUTING, COMPLETED, FAILED
    }

    @Data
    @Builder
    public static class SubTask {
        private int sequence;
        private String description;
        private String toolName;
        private String toolParams;
        private String result;
        @Builder.Default
        private SubTaskStatus status = SubTaskStatus.PENDING;
    }
}
