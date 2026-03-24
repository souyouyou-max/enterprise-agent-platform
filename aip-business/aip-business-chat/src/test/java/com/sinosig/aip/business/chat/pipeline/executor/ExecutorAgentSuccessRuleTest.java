package com.sinosig.aip.business.chat.pipeline.executor;

import com.sinosig.aip.business.chat.pipeline.executor.config.ExecutorProperties;
import com.sinosig.aip.common.ai.service.LlmService;
import com.sinosig.aip.core.context.AgentContext;
import com.sinosig.aip.core.context.AgentResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutorAgentSuccessRuleTest {

    private static ExecutorProperties defaultExecutorProperties() {
        return new ExecutorProperties();
    }

    @Test
    void execute_shouldReturnFailureWhenAllSubTasksFail() {
        LlmService llmService = mock(LlmService.class);
        ToolExecutionService toolExecutionService = mock(ToolExecutionService.class);
        SubTaskAnalysisService subTaskAnalysisService = mock(SubTaskAnalysisService.class);
        when(toolExecutionService.executeToolWithRetry("none", ""))
                .thenReturn("no-tool");
        when(subTaskAnalysisService.analyzeWithRetry(any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("llm boom"));

        ExecutorAgent agent = new ExecutorAgent(
                llmService,
                mock(ChatModel.class),
                toolExecutionService,
                subTaskAnalysisService,
                defaultExecutorProperties()
        );

        AgentContext context = AgentContext.builder()
                .taskId("t-1")
                .goal("goal")
                .subTasks(List.of(
                        AgentContext.SubTask.builder()
                                .sequence(1)
                                .description("s1")
                                .toolName("none")
                                .toolParams("")
                                .build()
                ))
                .build();

        AgentResult result = agent.execute(context);
        assertFalse(result.isSuccess());
    }

    @Test
    void execute_shouldReturnSuccessWhenAtLeastOneSubTaskSucceeds() {
        LlmService llmService = mock(LlmService.class);
        ToolExecutionService toolExecutionService = mock(ToolExecutionService.class);
        SubTaskAnalysisService subTaskAnalysisService = mock(SubTaskAnalysisService.class);
        when(toolExecutionService.executeToolWithRetry("none", ""))
                .thenReturn("no-tool");
        when(subTaskAnalysisService.analyzeWithRetry(any(), anyString(), anyString()))
                .thenReturn("ok")
                .thenThrow(new RuntimeException("llm boom"));

        ExecutorAgent agent = new ExecutorAgent(
                llmService,
                mock(ChatModel.class),
                toolExecutionService,
                subTaskAnalysisService,
                defaultExecutorProperties()
        );

        AgentContext context = AgentContext.builder()
                .taskId("t-2")
                .goal("goal")
                .subTasks(List.of(
                        AgentContext.SubTask.builder()
                                .sequence(1)
                                .description("s1")
                                .toolName("none")
                                .toolParams("")
                                .build(),
                        AgentContext.SubTask.builder()
                                .sequence(2)
                                .description("s2")
                                .toolName("none")
                                .toolParams("")
                                .build()
                ))
                .build();

        AgentResult result = agent.execute(context);
        assertTrue(result.isSuccess());
    }

    @Test
    void execute_shouldFailWhenToolNotRegistered() {
        LlmService llmService = mock(LlmService.class);
        ToolExecutionService toolExecutionService = mock(ToolExecutionService.class);
        SubTaskAnalysisService subTaskAnalysisService = mock(SubTaskAnalysisService.class);
        when(toolExecutionService.executeToolWithRetry("missingTool", "{}"))
                .thenThrow(new RuntimeException("tool missing"));

        ExecutorAgent agent = new ExecutorAgent(
                llmService,
                mock(ChatModel.class),
                toolExecutionService,
                subTaskAnalysisService,
                defaultExecutorProperties()
        );

        AgentContext context = AgentContext.builder()
                .taskId("t-3")
                .goal("goal")
                .subTasks(List.of(
                        AgentContext.SubTask.builder()
                                .sequence(1)
                                .description("s1")
                                .toolName("missingTool")
                                .toolParams("{}")
                                .build()
                ))
                .build();

        AgentResult result = agent.execute(context);
        assertFalse(result.isSuccess());
    }

    @Test
    void execute_shouldRetryLlmAndEventuallySucceed() {
        LlmService llmService = mock(LlmService.class);
        ToolExecutionService toolExecutionService = mock(ToolExecutionService.class);
        SubTaskAnalysisService subTaskAnalysisService = mock(SubTaskAnalysisService.class);
        when(toolExecutionService.executeToolWithRetry("getSalesData", "{\"department\":\"all\"}"))
                .thenReturn("{\"ok\":true}");
        when(subTaskAnalysisService.analyzeWithRetry(any(), anyString(), anyString()))
                .thenReturn("analysis ok");

        ExecutorAgent agent = new ExecutorAgent(
                llmService,
                mock(ChatModel.class),
                toolExecutionService,
                subTaskAnalysisService,
                defaultExecutorProperties()
        );

        AgentContext context = AgentContext.builder()
                .taskId("t-4")
                .goal("goal")
                .subTasks(List.of(
                        AgentContext.SubTask.builder()
                                .sequence(1)
                                .description("s1")
                                .toolName("getSalesData")
                                .toolParams("{\"department\":\"all\"}")
                                .build()
                ))
                .build();

        AgentResult result = agent.execute(context);
        assertTrue(result.isSuccess());
    }
}
