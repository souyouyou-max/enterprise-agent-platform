package com.enterprise.agent.task.service;

import com.enterprise.agent.common.core.enums.TaskStatus;
import com.enterprise.agent.common.core.exception.AgentException;
import com.enterprise.agent.common.core.response.PageResult;
import com.enterprise.agent.data.entity.AgentTask;
import com.enterprise.agent.data.service.AgentTaskDataService;
import com.enterprise.agent.task.event.AgentTaskEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * AgentTaskService - 任务 CRUD 业务服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskService {

    private final AgentTaskDataService dataService;
    private final KafkaTemplate<String, AgentTaskEvent> kafkaTemplate;
    private final AgentPipelineService pipelineService;

    /**
     * 创建并启动 Agent 任务
     */
    public AgentTask createAndStartTask(String taskName, String goal) {
        // 1. 持久化任务
        AgentTask task = dataService.createTask(taskName, goal);

        // 2. 发布创建事件
        publishEvent(AgentTaskEvent.builder()
                .taskId(task.getId())
                .taskName(taskName)
                .goal(goal)
                .currentStatus(TaskStatus.PENDING)
                .eventType("CREATED")
                .message("任务已创建，等待执行")
                .build());

        // 3. 异步启动 Pipeline
        pipelineService.executeAsync(task.getId(), taskName, goal);

        return task;
    }

    /**
     * 查询任务详情
     */
    public AgentTask getTask(Long taskId) {
        AgentTask task = dataService.getById(taskId);
        if (task == null) {
            throw new AgentException(404, "任务不存在: " + taskId);
        }
        return task;
    }

    /**
     * 分页查询任务列表
     */
    public PageResult<AgentTask> listTasks(long page, long size) {
        return dataService.getPage(page, size);
    }

    /**
     * 重试失败任务
     */
    public AgentTask retryTask(Long taskId) {
        AgentTask task = getTask(taskId);
        if (!TaskStatus.FAILED.name().equals(task.getStatus())) {
            throw new AgentException(400, "只有失败状态的任务才能重试");
        }

        dataService.updateStatus(taskId, TaskStatus.PENDING.name());
        publishEvent(AgentTaskEvent.builder()
                .taskId(taskId)
                .taskName(task.getTaskName())
                .previousStatus(TaskStatus.FAILED)
                .currentStatus(TaskStatus.PENDING)
                .eventType("RETRYING")
                .message("任务重试中")
                .build());

        pipelineService.executeAsync(taskId, task.getTaskName(), task.getGoal());
        return dataService.getById(taskId);
    }

    /**
     * 获取最终报告
     */
    public String getReport(Long taskId) {
        AgentTask task = getTask(taskId);
        if (!TaskStatus.COMPLETED.name().equals(task.getStatus())) {
            throw new AgentException(400, "任务尚未完成，当前状态: " + task.getStatus());
        }
        String report = task.getFinalReport();
        if (report == null || report.isBlank()) {
            throw new AgentException(404, "报告不存在");
        }
        return report;
    }

    private void publishEvent(AgentTaskEvent event) {
        try {
            kafkaTemplate.send(AgentTaskEvent.TOPIC, String.valueOf(event.getTaskId()), event);
            log.debug("[TaskService] Kafka 事件已发布: taskId={}, type={}", event.getTaskId(), event.getEventType());
        } catch (Exception e) {
            log.warn("[TaskService] Kafka 事件发布失败（非致命）: {}", e.getMessage());
        }
    }
}
