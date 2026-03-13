package com.enterprise.agent.service.agent.task.event;

import com.enterprise.agent.common.core.enums.TaskStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 任务状态流转 Kafka 事件
 */
@Data
@Builder
public class AgentTaskEvent {

    /** Kafka Topic */
    public static final String TOPIC = "agent-task-events";

    /** 任务 ID */
    private Long taskId;

    /** 任务名称 */
    private String taskName;

    /** 目标 */
    private String goal;

    /** 前一状态 */
    private TaskStatus previousStatus;

    /** 当前状态 */
    private TaskStatus currentStatus;

    /** 质量评分（Reviewer 完成后填充） */
    private Integer reviewScore;

    /** 事件类型（CREATED / STATUS_CHANGED / COMPLETED / FAILED） */
    private String eventType;

    /** 附加消息 */
    private String message;

    /** 事件时间 */
    @Builder.Default
    private LocalDateTime occurredAt = LocalDateTime.now();
}
