package com.sinosig.aip.core.orchestrator;

import com.sinosig.aip.common.core.enums.AgentRole;
import com.sinosig.aip.core.context.AgentContext;
import com.sinosig.aip.core.context.AgentResult;
import com.sinosig.aip.core.dispatcher.AgentDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentOrchestratorTest {

    private AgentDispatcher dispatcher;
    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        dispatcher = Mockito.mock(AgentDispatcher.class);
        orchestrator = new AgentOrchestrator(dispatcher);
    }

    /**
     * Reviewer 返回低分（reviewPassed=false）时，Pipeline 应重试 Executor，
     * 达到最大重试次数后继续进入 Communicator 生成报告。
     * <p>
     * 背景：ReviewerAgent 设计上始终返回 success=true，
     * 通过 context.reviewPassed 传递是否通过。
     */
    @Test
    void runPipeline_shouldRetryExecutorWhenReviewNotPassed() {
        AgentContext context = AgentContext.builder().taskId("t-1").goal("test").build();
        // context.reviewPassed 默认 false，模拟 Reviewer 始终给低分

        AgentResult plannerOk = AgentResult.builder().agentRole(AgentRole.PLANNER).success(true).build();
        AgentResult executorOk = AgentResult.builder().agentRole(AgentRole.EXECUTOR).success(true).build();
        // Reviewer 总返回 success=true，但 context.reviewPassed 保持 false（默认值）
        AgentResult reviewerLowScore = AgentResult.builder()
                .agentRole(AgentRole.REVIEWER).success(true).qualityScore(30).build();
        AgentResult communicatorOk = AgentResult.builder()
                .agentRole(AgentRole.COMMUNICATOR).success(true).output("final report").build();

        when(dispatcher.dispatch(eq(AgentRole.PLANNER), any())).thenReturn(plannerOk);
        when(dispatcher.dispatch(eq(AgentRole.EXECUTOR), any())).thenReturn(executorOk);
        when(dispatcher.dispatch(eq(AgentRole.REVIEWER), any())).thenReturn(reviewerLowScore);
        when(dispatcher.dispatch(eq(AgentRole.COMMUNICATOR), any())).thenReturn(communicatorOk);

        int maxRetries = 2;
        AgentResult result = orchestrator.runPipeline(context, maxRetries);

        // Planner 执行一次
        verify(dispatcher, times(1)).dispatch(eq(AgentRole.PLANNER), any());
        // Executor 应被重试 maxRetries 次
        verify(dispatcher, times(maxRetries)).dispatch(eq(AgentRole.EXECUTOR), any());
        // Reviewer 同样被调用 maxRetries 次
        verify(dispatcher, times(maxRetries)).dispatch(eq(AgentRole.REVIEWER), any());
        // 超出重试上限后仍进入 Communicator
        verify(dispatcher, times(1)).dispatch(eq(AgentRole.COMMUNICATOR), any());
        assertEquals("final report", result.getOutput());
    }

    /**
     * Planner 失败时，Pipeline 应立即终止，不调用后续步骤。
     */
    @Test
    void runPipeline_shouldStopWhenPlannerFails() {
        AgentContext context = AgentContext.builder().taskId("t-2").goal("test").build();
        AgentResult plannerFail = AgentResult.failure(AgentRole.PLANNER, "planner error");

        when(dispatcher.dispatch(eq(AgentRole.PLANNER), any())).thenReturn(plannerFail);

        AgentResult result = orchestrator.runPipeline(context, 2);

        assertFalse(result.isSuccess());
        verify(dispatcher, never()).dispatch(eq(AgentRole.EXECUTOR), any());
        verify(dispatcher, never()).dispatch(eq(AgentRole.REVIEWER), any());
        verify(dispatcher, never()).dispatch(eq(AgentRole.COMMUNICATOR), any());
    }

    /**
     * Executor 失败时，Pipeline 应立即终止，不调用 Reviewer 和 Communicator。
     */
    @Test
    void runPipeline_shouldStopWhenExecutorFails() {
        AgentContext context = AgentContext.builder().taskId("t-3").goal("test").build();
        AgentResult plannerOk = AgentResult.builder().agentRole(AgentRole.PLANNER).success(true).build();
        AgentResult executorFail = AgentResult.failure(AgentRole.EXECUTOR, "executor error");

        when(dispatcher.dispatch(eq(AgentRole.PLANNER), any())).thenReturn(plannerOk);
        when(dispatcher.dispatch(eq(AgentRole.EXECUTOR), any())).thenReturn(executorFail);

        AgentResult result = orchestrator.runPipeline(context, 2);

        assertFalse(result.isSuccess());
        verify(dispatcher, never()).dispatch(eq(AgentRole.REVIEWER), any());
        verify(dispatcher, never()).dispatch(eq(AgentRole.COMMUNICATOR), any());
    }

    /**
     * Reviewer 质量通过（context.reviewPassed=true）时，Pipeline 只执行一次 Executor，
     * 直接进入 Communicator。
     */
    @Test
    void runPipeline_shouldNotRetryWhenReviewPassed() {
        AgentContext context = AgentContext.builder().taskId("t-4").goal("test").build();
        AgentResult plannerOk = AgentResult.builder().agentRole(AgentRole.PLANNER).success(true).build();
        AgentResult executorOk = AgentResult.builder().agentRole(AgentRole.EXECUTOR).success(true).build();
        AgentResult communicatorOk = AgentResult.builder()
                .agentRole(AgentRole.COMMUNICATOR).success(true).output("report").build();

        // Reviewer 调用时将 context.reviewPassed 设为 true，模拟质量通过
        when(dispatcher.dispatch(eq(AgentRole.PLANNER), any())).thenReturn(plannerOk);
        when(dispatcher.dispatch(eq(AgentRole.EXECUTOR), any())).thenReturn(executorOk);
        when(dispatcher.dispatch(eq(AgentRole.REVIEWER), any())).thenAnswer(inv -> {
            AgentContext ctx = inv.getArgument(1);
            ctx.setReviewPassed(true);
            ctx.setReviewScore(85);
            return AgentResult.builder().agentRole(AgentRole.REVIEWER).success(true).qualityScore(85).build();
        });
        when(dispatcher.dispatch(eq(AgentRole.COMMUNICATOR), any())).thenReturn(communicatorOk);

        AgentResult result = orchestrator.runPipeline(context, 3);

        // Executor 与 Reviewer 各只调用一次
        verify(dispatcher, times(1)).dispatch(eq(AgentRole.EXECUTOR), any());
        verify(dispatcher, times(1)).dispatch(eq(AgentRole.REVIEWER), any());
        verify(dispatcher, times(1)).dispatch(eq(AgentRole.COMMUNICATOR), any());
        assertTrue(result.isSuccess());
    }
}
