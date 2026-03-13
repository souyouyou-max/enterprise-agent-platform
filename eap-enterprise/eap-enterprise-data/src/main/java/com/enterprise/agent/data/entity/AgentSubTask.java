package com.enterprise.agent.data.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Agent 子任务实体（对应 agent_sub_task 表）
 */
@Data
@TableName("agent_sub_task")
public class AgentSubTask {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("task_id")
    private Long taskId;

    @TableField("sequence")
    private Integer sequence;

    @TableField("description")
    private String description;

    @TableField("tool_name")
    private String toolName;

    @TableField("tool_params")
    private String toolParams;

    @TableField("result")
    private String result;

    @TableField("status")
    private String status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
