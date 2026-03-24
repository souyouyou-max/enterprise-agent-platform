package com.sinosig.aip.business.chat.pipeline.executor;

import com.sinosig.aip.business.chat.pipeline.executor.config.ExecutorProperties;
import com.sinosig.aip.common.ai.service.LlmService;
import com.sinosig.aip.common.core.enums.AgentRole;
import com.sinosig.aip.core.agent.BaseAgent;
import com.sinosig.aip.core.context.AgentContext;
import com.sinosig.aip.core.context.AgentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * ExecutorAgent - 遍历子任务，调用对应企业工具，支持最多 3 次重试
 */
@Slf4j
@Component
public class ExecutorAgent extends BaseAgent {

    private final ToolExecutionService toolExecutionService;
    private final SubTaskAnalysisService subTaskAnalysisService;
    private final ExecutorProperties executorProperties;

    public ExecutorAgent(LlmService llmService, ChatModel chatModel,
                         ToolExecutionService toolExecutionService,
                         SubTaskAnalysisService subTaskAnalysisService,
                         ExecutorProperties executorProperties) {
        super(llmService, chatModel);
        this.toolExecutionService = toolExecutionService;
        this.subTaskAnalysisService = subTaskAnalysisService;
        this.executorProperties = executorProperties;
    }

    @Override
    public AgentRole getRole() {
        return AgentRole.EXECUTOR;
    }

    @Override
    protected String getSystemPrompt() {
        return "";
    }

    @Override
    public AgentResult execute(AgentContext context) {
        logStart(context);
        Map<Integer, String> executionResults = new HashMap<>();

        for (AgentContext.SubTask subTask : context.getSubTasks()) {
            log.info("[Executor] 执行子任务 {}: {}", subTask.getSequence(), subTask.getDescription());
            subTask.setStatus(AgentContext.SubTaskStatus.EXECUTING);

            try {
                String result = executeSubTaskWithRetry(subTask, context);
                executionResults.put(subTask.getSequence(), result);
                subTask.setResult(result);
                subTask.setStatus(AgentContext.SubTaskStatus.COMPLETED);
                log.info("[Executor] 子任务 {} 完成", subTask.getSequence());

            } catch (Exception e) {
                String errorMsg = "子任务执行失败: " + e.getMessage();
                log.error("[Executor] 子任务 {} 失败: {}", subTask.getSequence(), e.getMessage());
                executionResults.put(subTask.getSequence(), errorMsg);
                subTask.setResult(errorMsg);
                subTask.setStatus(AgentContext.SubTaskStatus.FAILED);
            }
        }

        context.setExecutionResults(executionResults);

        long completed = context.getSubTasks().stream().filter(t -> AgentContext.SubTaskStatus.COMPLETED == t.getStatus()).count();
        long total = context.getSubTasks().size();
        int threshold = Math.max(0, Math.min(100, executorProperties.getSuccessThresholdPercent()));
        boolean success = total > 0 && (completed * 100 / total) >= threshold;
        String summary = String.format("执行完成: %d/%d 个子任务成功（成功率%d%%，阈值%d%%）",
                completed, total, total > 0 ? (completed * 100 / total) : 0, threshold);
        if (!success) {
            log.warn("[Executor] 子任务成功率不达标: {}/{} (阈值{}%)", completed, total, threshold);
        }

        AgentResult result = AgentResult.builder()
                .agentRole(AgentRole.EXECUTOR)
                .output(summary)
                .subTaskResults(executionResults)
                .success(success)
                .build();

        logEnd(context, result);
        return result;
    }

    private String executeSubTaskWithRetry(AgentContext.SubTask subTask, AgentContext context) {
        String rawData = toolExecutionService.executeToolWithRetry(subTask.getToolName(), subTask.getToolParams());
        return subTaskAnalysisService.analyzeWithRetry(subTask, context.getGoal(), rawData);
    }
}
