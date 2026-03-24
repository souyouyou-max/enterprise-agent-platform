package com.sinosig.aip.business.chat.pipeline.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinosig.aip.common.ai.service.LlmService;
import com.sinosig.aip.core.context.AgentContext;
import com.sinosig.aip.core.context.AgentResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlannerAgentFallbackTest {

    @Test
    void execute_shouldFallbackWhenLlmReturnsEmptyArray() {
        LlmService llmService = mock(LlmService.class);
        when(llmService.chatWithSystem(anyString(), anyString())).thenReturn("[]");

        PlannerAgent agent = new PlannerAgent(
                llmService,
                mock(ChatModel.class),
                new ObjectMapper()
        );

        AgentContext context = AgentContext.builder()
                .taskId("t-1")
                .goal("分析销售趋势")
                .build();

        AgentResult result = agent.execute(context);
        assertTrue(result.isSuccess());
        assertEquals(3, context.getSubTasks().size());
    }
}
