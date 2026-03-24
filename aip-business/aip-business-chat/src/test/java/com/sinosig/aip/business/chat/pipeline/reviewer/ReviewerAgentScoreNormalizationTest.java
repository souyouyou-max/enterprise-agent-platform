package com.sinosig.aip.business.chat.pipeline.reviewer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinosig.aip.common.ai.service.LlmService;
import com.sinosig.aip.core.context.AgentContext;
import com.sinosig.aip.core.context.AgentResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReviewerAgentScoreNormalizationTest {

    @Test
    void execute_shouldClampScoreAndEnforceThreshold() {
        LlmService llmService = mock(LlmService.class);
        when(llmService.chatWithSystem(anyString(), anyString()))
                .thenReturn("{\"score\":120,\"passed\":true,\"issues\":[],\"summary\":\"ok\"}");

        ReviewerAgent agent = new ReviewerAgent(
                llmService,
                mock(ChatModel.class),
                new ObjectMapper()
        );

        AgentContext context = AgentContext.builder()
                .taskId("t-1")
                .goal("goal")
                .build();

        AgentResult result = agent.execute(context);
        assertTrue(result.isSuccess());
        assertEquals(100, context.getReviewScore());
        assertTrue(context.isReviewPassed());
    }

    @Test
    void execute_shouldNotPassWhenScoreBelowThresholdEvenIfPassedTrue() {
        LlmService llmService = mock(LlmService.class);
        when(llmService.chatWithSystem(anyString(), anyString()))
                .thenReturn("{\"score\":10,\"passed\":true,\"issues\":[\"bad\"],\"summary\":\"low\"}");

        ReviewerAgent agent = new ReviewerAgent(
                llmService,
                mock(ChatModel.class),
                new ObjectMapper()
        );

        AgentContext context = AgentContext.builder()
                .taskId("t-2")
                .goal("goal")
                .build();

        AgentResult result = agent.execute(context);
        assertTrue(result.isSuccess());
        assertEquals(10, context.getReviewScore());
        assertFalse(context.isReviewPassed());
    }
}
