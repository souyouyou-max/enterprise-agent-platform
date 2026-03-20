package com.enterprise.agent.business.task.service;

import com.enterprise.agent.common.core.enums.AgentRole;
import com.enterprise.agent.common.core.enums.ReportStyle;
import com.enterprise.agent.common.core.enums.TaskStatus;
import com.enterprise.agent.core.context.AgentContext;
import com.enterprise.agent.core.context.AgentResult;
import com.enterprise.agent.core.orchestrator.AgentOrchestrator;
import com.enterprise.agent.data.entity.AgentSubTask;
import com.enterprise.agent.data.service.AgentTaskDataService;
import com.enterprise.agent.business.task.event.AgentTaskEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentPipelineService - 异步执行 Agent Pipeline
 * Planner → Executor → Reviewer → Communicator
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentPipelineService {

    private final AgentOrchestrator orchestrator;
    private final AgentTaskDataService dataService;
    private final KafkaTemplate<String, AgentTaskEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final int MAX_REVIEW_RETRIES = 2;

    /**
     * 异步执行 Pipeline
     */
    @Async("agentTaskExecutor")
    public void executeAsync(Long taskId, String taskName, String goal) {
        log.info("[Pipeline] 异步任务开始, taskId={}", taskId);

        // 构建上下文
        AgentContext context = AgentContext.builder()
                .taskId(String.valueOf(taskId))
                .taskName(taskName)
                .goal(goal)
                .reportStyle(ReportStyle.DETAILED)
                .build();

        try {
            // Step 1: PLANNING
            updateStatus(taskId, TaskStatus.PENDING, TaskStatus.PLANNING);

            // Step 2: 执行 Planner
            AgentResult plannerResult = orchestrator.runAgent(AgentRole.PLANNER, context);
            if (!plannerResult.isSuccess()) {
                failTask(taskId, taskName, "Planner 失败: " + plannerResult.getErrorMessage());
                return;
            }
            persistPlannerResult(taskId, plannerResult, context);

            // Step 3: EXECUTING
            updateStatus(taskId, TaskStatus.PLANNING, TaskStatus.EXECUTING);

            // Step 4: Executor + Reviewer (with retry loop)
            int reviewRetry = 0;
            boolean reviewPassed = false;

            while (reviewRetry < MAX_REVIEW_RETRIES && !reviewPassed) {
                if (reviewRetry > 0) {
                    log.warn("[Pipeline] 质量不达标，第 {} 次重试 Executor", reviewRetry);
                    context.getReviewIssues().clear();
                    // 重试时状态需从 REVIEWING 回退至 EXECUTING，保持状态机一致
                    updateStatus(taskId, TaskStatus.REVIEWING, TaskStatus.EXECUTING);
                }

                AgentResult executorResult = orchestrator.runAgent(AgentRole.EXECUTOR, context);
                if (!executorResult.isSuccess()) {
                    failTask(taskId, taskName, "Executor 失败: " + executorResult.getErrorMessage());
                    return;
                }
                persistExecutorResult(taskId, executorResult, context);

                // Step 5: REVIEWING
                updateStatus(taskId, TaskStatus.EXECUTING, TaskStatus.REVIEWING);
                AgentResult reviewerResult = orchestrator.runAgent(AgentRole.REVIEWER, context);

                reviewPassed = context.isReviewPassed();
                reviewRetry++;
            }

            publishEvent(AgentTaskEvent.builder()
                    .taskId(String.valueOf(taskId)).taskName(taskName)
                    .currentStatus(TaskStatus.REVIEWING)
                    .eventType("REVIEWED")
                    .reviewScore(context.getReviewScore())
                    .message("审查完成，评分: " + context.getReviewScore())
                    .build());

            // 超出最大重试次数后质量仍未达标，直接失败，避免用低质量结果生成报告
            if (!reviewPassed) {
                failTask(taskId, taskName,
                        String.format("质量审查未通过，最终评分 %d/100（阈值60），已重试 %d 次",
                                context.getReviewScore(), MAX_REVIEW_RETRIES));
                return;
            }

            // Step 6: COMMUNICATING
            updateStatus(taskId, TaskStatus.REVIEWING, TaskStatus.COMMUNICATING);

            AgentResult communicatorResult = orchestrator.runAgent(AgentRole.COMMUNICATOR, context);
            if (!communicatorResult.isSuccess()) {
                failTask(taskId, taskName, "Communicator 失败: " + communicatorResult.getErrorMessage());
                return;
            }

            // Step 7: COMPLETED
            dataService.updateTaskResult(
                    taskId,
                    null,
                    null,
                    context.getReviewScore(),
                    context.getFinalReport(),
                    TaskStatus.COMPLETED.name()
            );

            publishEvent(AgentTaskEvent.builder()
                    .taskId(String.valueOf(taskId)).taskName(taskName)
                    .previousStatus(TaskStatus.COMMUNICATING)
                    .currentStatus(TaskStatus.COMPLETED)
                    .eventType("COMPLETED")
                    .reviewScore(context.getReviewScore())
                    .message("Pipeline 执行完成！")
                    .build());

            log.info("[Pipeline] 任务完成, taskId={}, 评分={}", taskId, context.getReviewScore());

        } catch (Exception e) {
            log.error("[Pipeline] 任务异常, taskId={}: {}", taskId, e.getMessage(), e);
            failTask(taskId, taskName, "系统异常: " + e.getMessage());
        }
    }

    private void persistPlannerResult(Long taskId, AgentResult result, AgentContext context) {
        try {
            // 持久化子任务
            List<AgentSubTask> subTasks = new ArrayList<>();
            for (AgentContext.SubTask st : context.getSubTasks()) {
                AgentSubTask entity = new AgentSubTask();
                entity.setSequence(st.getSequence());
                entity.setDescription(st.getDescription());
                entity.setToolName(st.getToolName());
                entity.setToolParams(st.getToolParams());
                entity.setStatus(st.getStatus().name()); // SubTaskStatus 枚举转字符串写入数据库
                subTasks.add(entity);
            }
            dataService.saveSubTasks(taskId, subTasks);

            dataService.updateTaskResult(taskId, result.getOutput(), null, null, null, TaskStatus.PLANNING.name());
        } catch (Exception e) {
            log.warn("[Pipeline] 持久化 Planner 结果失败: {}", e.getMessage());
        }
    }

    private void persistExecutorResult(Long taskId, AgentResult result, AgentContext context) {
        try {
            String executorSummary = objectMapper.writeValueAsString(context.getExecutionResults());
            dataService.updateTaskResult(taskId, null, executorSummary, null, null, TaskStatus.EXECUTING.name());
        } catch (Exception e) {
            log.warn("[Pipeline] 持久化 Executor 结果失败: {}", e.getMessage());
        }
    }

    private void updateStatus(Long taskId, TaskStatus previous, TaskStatus current) {
        dataService.updateStatus(taskId, current.name());
        publishEvent(AgentTaskEvent.builder()
                .taskId(String.valueOf(taskId))
                .previousStatus(previous)
                .currentStatus(current)
                .eventType("STATUS_CHANGED")
                .message("状态变更: " + previous.name() + " → " + current.name())
                .build());
    }

    private void failTask(Long taskId, String taskName, String errorMessage) {
        dataService.updateStatus(taskId, TaskStatus.FAILED.name());
        publishEvent(AgentTaskEvent.builder()
                .taskId(String.valueOf(taskId)).taskName(taskName)
                .currentStatus(TaskStatus.FAILED)
                .eventType("FAILED")
                .message(errorMessage)
                .build());
        log.error("[Pipeline] 任务失败, taskId={}: {}", taskId, errorMessage);
    }

    private void publishEvent(AgentTaskEvent event) {
        try {
            kafkaTemplate.send(AgentTaskEvent.TOPIC, String.valueOf(event.getTaskId()), event);
        } catch (Exception e) {
            log.warn("[Pipeline] Kafka 事件发布失败: {}", e.getMessage());
        }
    }
}
