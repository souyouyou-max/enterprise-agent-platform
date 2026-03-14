package com.enterprise.agent.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 任务实体（对应 agent_task 表）
 */
@Data
@TableName("agent_task")
public class AgentTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("task_name")
    private String taskName;

    @TableField("goal")
    private String goal;

    @TableField("status")
    private String status;

    @TableField("planner_result")
    private String plannerResult;

    @TableField("executor_result")
    private String executorResult;

    @TableField("reviewer_score")
    private Integer reviewerScore;

    @TableField("final_report")
    private String finalReport;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
