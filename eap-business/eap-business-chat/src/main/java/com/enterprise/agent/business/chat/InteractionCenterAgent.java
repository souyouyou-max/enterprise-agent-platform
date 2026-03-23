package com.enterprise.agent.business.chat;

import com.enterprise.agent.business.chat.config.EapChatProperties;
import com.enterprise.agent.business.chat.toolkit.AgentOrchestrationToolkit;
import com.enterprise.agent.business.chat.toolkit.ConversationOcrToolkit;
import com.enterprise.agent.common.ai.service.LlmService;
import com.enterprise.agent.common.core.enums.AgentRole;
import com.enterprise.agent.core.agent.BaseAgent;
import com.enterprise.agent.core.context.AgentContext;
import com.enterprise.agent.core.context.AgentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * InteractionCenterAgent - AI 交互中心智能体（统一入口）
 * <p>
 * 职责：
 * 1. 接收用户自然语言输入
 * 2. 将四个业务 Agent 工具注册给 LLM，由 LLM 自主决定调用哪些 Agent 及调用顺序
 * 3. 汇总工具调用结果，输出综合分析报告
 * 4. 维护多轮对话记忆窗口（由 ConversationSession 管理）
 * <p>
 * 编排模式：
 * LLM 持有 AgentOrchestrationToolkit 的四个工具：
 * - discoverClues(orgCode)   → ClueDiscoveryAgent
 * - analyzeRisk(orgCode)     → RiskAnalysisAgent
 * - checkMonitoring(orgCode) → MonitoringAgent
 */
@Slf4j
@Service
public class InteractionCenterAgent extends BaseAgent {

    private static final String SYSTEM_PROMPT = """
            你是企业智能助手，具备以下能力：
            1）直接用自然语言回答用户的一般问题；
            2）当用户提供机构编码/orgCode 并要求做风控分析、监测预警或线索发现时，
                可以调用 discoverClues / analyzeRisk / checkMonitoring 等工具；
            3）当用户上传图片、证件或PDF/Word/PPT等文件，并要求“识别文字”“看清楚内容”“提取字段”时：
                - 若主要诉求是结构化识别（如证件、票据、扫描件），优先调用 dazhiOcrGeneralForChat；
                - 若主要诉求是理解/总结（如阅读报告截图、长文档并提炼要点），优先调用 img2TextForChat。
            在调用工具前，请根据用户话术和上下文自行判断是否有必要使用工具；如无需调用工具，可直接回答。
            """;

    private final AgentOrchestrationToolkit orchestrationToolkit;
    private final ConversationOcrToolkit conversationOcrToolkit;
    private final ChatClient advisorChatClient;
    private final EapChatProperties chatProperties;

    public InteractionCenterAgent(LlmService llmService,
                                  ChatModel chatModel,
                                  @Lazy AgentOrchestrationToolkit orchestrationToolkit,
                                  ConversationOcrToolkit conversationOcrToolkit,
                                  @Qualifier("advisorChatClient") ChatClient advisorChatClient,
                                  EapChatProperties chatProperties) {
        super(llmService, chatModel);
        this.orchestrationToolkit = orchestrationToolkit;
        this.conversationOcrToolkit = conversationOcrToolkit;
        this.advisorChatClient = advisorChatClient;
        this.chatProperties = chatProperties;
        log.info("[InteractionCenter] 初始化完成，ChatModel 类型: {}", chatModel.getClass().getSimpleName());
        if (chatModel instanceof org.springframework.ai.openai.OpenAiChatModel openAiModel) {
            try {
                var options = openAiModel.getDefaultOptions();
                log.info("[InteractionCenter] OpenAI model={}, base-url 请看 reactor.netty 日志",
                        options.getModel());
            } catch (Exception e) {
                log.warn("[InteractionCenter] 无法读取 OpenAI 配置: {}", e.getMessage());
            }
        }
    }

    @Override
    public AgentRole getRole() {
        return AgentRole.INTERACTION_CENTER;
    }

    @Override
    protected String getSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    /**
     * 实现 BaseAgent.execute()：支持 AgentOrchestrator 以任务上下文方式调用。
     * 使用 task-{taskId} 作为会话 ID，goal 作为用户消息。
     */
    @Override
    public AgentResult execute(AgentContext context) {
        logStart(context);
        String sessionId = "task-" + context.getTaskId();
        InteractionResult result = chat(sessionId, context.getGoal());
        AgentResult agentResult = AgentResult.builder()
                .agentRole(AgentRole.INTERACTION_CENTER)
                .output(result.getResponse())
                .success(true)
                .build();
        logEnd(context, agentResult);
        return agentResult;
    }

    /**
     * 核心多轮对话方法：由 LLM 自主决策调用哪些 Agent 工具
     * 使用 advisorChatClient（含 MessageChatMemoryAdvisor），与 chatStream() 共享同一记忆
     *
     * @param sessionId   会话 ID（由客户端持有，跨请求复用）
     * @param userMessage 用户自然语言输入
     * @return 包含 AI 综合回复的 InteractionResult
     */
    public InteractionResult chat(String sessionId, String userMessage) {
        String safeMessage = sanitizeInput(userMessage);
        if (safeMessage.isBlank()) {
            safeMessage = "你好";
        }
        log.info("[InteractionCenter] chat, sessionId={}, message={}", sessionId,
                safeMessage.substring(0, Math.min(50, safeMessage.length())));

        // 使用 advisorChatClient（含 MessageChatMemoryAdvisor），记忆由 Advisor 自动管理
        String response;
        try {
            ChatClient.ChatClientRequestSpec requestSpec = advisorChatClient
                    .prompt()
                    .system(SYSTEM_PROMPT)
                    .advisors(advisor -> advisor.param("conversation_id", sessionId))
                    .user(safeMessage);
            boolean useOrgTools = shouldUseTools(safeMessage);
            requestSpec = applyTools(requestSpec, useOrgTools);
            response = requestSpec.call().content();
            if (response == null) {
                response = "";
            }
            log.info("[InteractionCenter] chat 完成, sessionId={}, length={}", sessionId, response.length());
        } catch (Exception e) {
            log.error("[InteractionCenter] ChatClient 调用失败，降级为直接回答: {}", e.getMessage(), e);
            response = llmService.chatWithSystem(SYSTEM_PROMPT, safeMessage);
        }

        return InteractionResult.builder()
                .sessionId(sessionId)
                .userMessage(userMessage)
                .agentType(InteractionResult.AgentType.GENERAL)
                .response(response)
                .usedTools(List.of())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * 流式对话：逐 token 返回 AI 响应，同步维护会话历史
     *
     * @param sessionId   会话 ID
     * @param userMessage 用户自然语言输入
     * @return 每个 token 的 Flux 流
     */
    public Flux<String> chatStream(String sessionId, String userMessage) {
        String safeMessage = sanitizeInput(userMessage);
        if (safeMessage.isBlank()) {
            safeMessage = "你好";
        }
        final String finalMessage = safeMessage;
        log.info("[InteractionCenter] chatStream, sessionId={}, message={}", sessionId,
                finalMessage.substring(0, Math.min(50, finalMessage.length())));
        final boolean useOrgTools = shouldUseTools(finalMessage);

        ChatClient.ChatClientRequestSpec streamSpec = advisorChatClient
                .prompt()
                .system(SYSTEM_PROMPT)
                .advisors(advisor -> advisor.param("conversation_id", sessionId))
                .user(finalMessage);
        streamSpec = applyTools(streamSpec, useOrgTools);

        // 直接透传流式 token，不在此处 collectList——collectList 会等全部 token 到齐后才开始
        // 发送，丧失流式的逐字输出体验。空 chunk 降级逻辑改为 onErrorResume 兜底。
        final ChatClient.ChatClientRequestSpec finalStreamSpec = streamSpec;
        return finalStreamSpec.stream()
                .content()
                .filter(chunk -> chunk != null && !chunk.isBlank())
                .switchIfEmpty(Flux.defer(() -> {
                    // 流为空时降级为普通调用再逐字分片模拟流式（兼容部分网关/模型不支持 SSE 的场景）
                    log.warn("[InteractionCenter] chatStream 空响应，降级为非流式调用后分片返回, sessionId={}", sessionId);
                    ChatClient.ChatClientRequestSpec fallbackSpec = advisorChatClient
                            .prompt()
                            .system(SYSTEM_PROMPT)
                            .advisors(advisor -> advisor.param("conversation_id", sessionId))
                            .user(finalMessage);
                    ChatClient.ChatClientRequestSpec fb = applyTools(fallbackSpec, useOrgTools);
                    String fallback = fb.call().content();
                    if (fallback == null || fallback.isBlank()) {
                        return Flux.just("抱歉，暂时没有拿到模型回复，请稍后重试。");
                    }
                    return splitByCharacter(fallback, 8);
                }))
                .onErrorResume(e -> {
                    log.error("[InteractionCenter] chatStream 最终兜底失败: {}", e.getMessage(), e);
                    return Flux.just("抱歉，模型服务暂时不可用，请稍后重试。");
                })
                .doOnNext(chunk -> log.debug("[InteractionCenter] stream chunk: {} chars", chunk.length()))
                .doOnComplete(() -> log.info("[InteractionCenter] chatStream 完成, sessionId={}", sessionId))
                .doOnError(e -> log.error("[InteractionCenter] chatStream 异常: {}", e.getMessage(), e));
    }

    /**
     * 将工具注册到 requestSpec，集中管理工具注入逻辑，避免在 chat/chatStream/fallback 三处重复。
     * <p>
     * 规则：
     * - conversationOcrToolkit 始终注册（OCR 能力对所有对话可用）
     * - orchestrationToolkit 仅在 useOrgTools=true 时注册（机构编排工具，普通对话屏蔽以防干扰）
     */
    private ChatClient.ChatClientRequestSpec applyTools(
            ChatClient.ChatClientRequestSpec spec, boolean useOrgTools) {
        if (conversationOcrToolkit != null && useOrgTools) {
            return spec.tools(orchestrationToolkit, conversationOcrToolkit);
        } else if (conversationOcrToolkit != null) {
            return spec.tools(conversationOcrToolkit);
        } else if (useOrgTools) {
            return spec.tools(orchestrationToolkit);
        }
        return spec;
    }

    private Flux<String> splitByCharacter(String text, long delayMillis) {
        return Flux.fromArray(text.split(""))
                .delayElements(Duration.ofMillis(delayMillis));
    }

    /**
     * 仅在明显是业务编排诉求时启用工具，避免普通闲聊被工具说明干扰。
     * <p>
     * 关键词列表由 {@code eap.pipeline.chat.org-tool-keywords} 配置，支持 Nacos 动态刷新。
     */
    private boolean shouldUseTools(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        if (message.matches(".*\\b\\d{6,}\\b.*")) {
            return true;
        }
        List<String> keywords = chatProperties.getOrgToolKeywords();
        String lower = message.toLowerCase();
        return keywords.stream().anyMatch(lower::contains);
    }

}
