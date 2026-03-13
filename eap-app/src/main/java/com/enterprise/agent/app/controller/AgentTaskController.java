package com.enterprise.agent.app.controller;

import com.enterprise.agent.common.core.response.PageResult;
import com.enterprise.agent.common.core.response.ResponseResult;
import com.enterprise.agent.data.entity.AgentTask;
import com.enterprise.agent.task.service.AgentTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * Agent 任务 REST API
 */
@Tag(name = "Agent 任务管理", description = "创建、查询、重试 Agent 任务，获取分析报告")
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class AgentTaskController {

    private final AgentTaskService taskService;

    @Operation(summary = "创建并启动任务", description = "传入目标描述，异步触发 Planner→Executor→Reviewer→Communicator Pipeline")
    @PostMapping
    public ResponseResult<AgentTask> createTask(@Valid @RequestBody CreateTaskRequest request) {
        AgentTask task = taskService.createAndStartTask(request.getTaskName(), request.getGoal());
        return ResponseResult.success(task);
    }

    @Operation(summary = "查询任务详情")
    @GetMapping("/{id}")
    public ResponseResult<AgentTask> getTask(@Parameter(description = "任务ID") @PathVariable Long id) {
        return ResponseResult.success(taskService.getTask(id));
    }

    @Operation(summary = "分页查询任务列表")
    @GetMapping
    public ResponseResult<PageResult<AgentTask>> listTasks(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size) {
        return ResponseResult.success(taskService.listTasks(page, size));
    }

    @Operation(summary = "重试失败任务")
    @PostMapping("/{id}/retry")
    public ResponseResult<AgentTask> retryTask(@PathVariable Long id) {
        return ResponseResult.success(taskService.retryTask(id));
    }

    @Operation(summary = "获取最终分析报告（Markdown）")
    @GetMapping("/{id}/report")
    public ResponseResult<String> getReport(@PathVariable Long id) {
        return ResponseResult.success(taskService.getReport(id));
    }

    @Data
    public static class CreateTaskRequest {
        @NotBlank(message = "任务名称不能为空")
        @Size(max = 200, message = "任务名称不超过200字符")
        private String taskName;

        @NotBlank(message = "目标描述不能为空")
        @Size(max = 2000, message = "目标描述不超过2000字符")
        private String goal;
    }
}
