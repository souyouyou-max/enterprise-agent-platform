package com.sinosig.aip.business.chat.pipeline.executor;

import com.sinosig.aip.business.chat.pipeline.executor.config.ExecutorProperties;
import com.sinosig.aip.common.ai.service.LlmService;
import com.sinosig.aip.core.context.AgentContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubTaskAnalysisServiceTest {

    private static ExecutorProperties defaultProps() {
        return new ExecutorProperties();
    }

    @Test
    void analyzeWithRetry_shouldSucceedOnSecondTry() {
        LlmService llmService = mock(LlmService.class);
        when(llmService.chatWithSystem(anyString(), anyString()))
                .thenThrow(new RuntimeException("transient"))
                .thenReturn("ok");

        SubTaskAnalysisService service = new SubTaskAnalysisService(llmService, defaultProps());
        AgentContext.SubTask subTask = AgentContext.SubTask.builder()
                .sequence(1)
                .description("分析")
                .build();

        String result = service.analyzeWithRetry(subTask, "goal", "{\"a\":1}");
        assertEquals("ok", result);
    }

    @Test
    void analyzeWithRetry_shouldThrowAfterAllRetriesFailed() {
        LlmService llmService = mock(LlmService.class);
        when(llmService.chatWithSystem(anyString(), anyString()))
                .thenThrow(new RuntimeException("down"));

        ExecutorProperties properties = defaultProps();
        properties.setMaxLlmRetries(1);
        SubTaskAnalysisService service = new SubTaskAnalysisService(llmService, properties);
        AgentContext.SubTask subTask = AgentContext.SubTask.builder()
                .sequence(1)
                .description("分析")
                .build();

        assertThrows(RuntimeException.class, () -> service.analyzeWithRetry(subTask, "goal", "{\"a\":1}"));
        verify(llmService, times(1)).chatWithSystem(anyString(), anyString());
    }
}
