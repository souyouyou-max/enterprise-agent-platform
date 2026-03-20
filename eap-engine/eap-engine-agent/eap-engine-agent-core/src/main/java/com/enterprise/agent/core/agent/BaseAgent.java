package com.enterprise.agent.core.agent;

import com.enterprise.agent.common.ai.service.LlmService;
import com.enterprise.agent.common.core.enums.AgentRole;
import com.enterprise.agent.core.context.AgentContext;
import com.enterprise.agent.core.context.AgentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/**
 * BaseAgent - 所有 Agent 的抽象基类
 * 封装 ChatClient 构建、系统提示注入、重试逻辑
 */
@Slf4j
public abstract class BaseAgent {

    protected final LlmService llmService;
    protected final ChatModel chatModel;

    protected BaseAgent(LlmService llmService, ChatModel chatModel) {
        this.llmService = llmService;
        this.chatModel = chatModel;
    }

    /**
     * 获取该 Agent 的角色
     */
    public abstract AgentRole getRole();

    /**
     * 获取系统提示词
     */
    protected abstract String getSystemPrompt();

    /**
     * 执行 Agent 核心逻辑
     *
     * @param context 任务上下文
     * @return 执行结果
     */
    public abstract AgentResult execute(AgentContext context);

    /**
     * 构建 ChatClient.Builder（注入系统提示）
     * 子类可链式调用 .defaultTools(toolkit).build() 注册专属技能集
     */
    protected ChatClient.Builder buildChatClient() {
        return ChatClient.builder(chatModel)
                .defaultSystem(getSystemPrompt());
    }

    /**
     * 带重试的 LLM 调用（最多 maxRetries 次，指数退避：1s → 2s → 4s …）
     */
    protected String callLlmWithRetry(String prompt, int maxRetries) {
        int attempts = 0;
        Exception lastException = null;
        while (attempts < maxRetries) {
            try {
                return llmService.simpleChat(prompt);
            } catch (Exception e) {
                lastException = e;
                attempts++;
                log.warn("[{}] LLM 调用失败，第 {}/{} 次重试: {}", getRole(), attempts, maxRetries, e.getMessage());
                if (attempts < maxRetries) {
                    try {
                        // 指数退避：第 1 次等 1s，第 2 次等 2s，第 3 次等 4s …
                        Thread.sleep(1000L << (attempts - 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw new RuntimeException("LLM 调用失败，已重试 " + maxRetries + " 次", lastException);
    }

    /**
     * 防 Prompt 注入清洗
     */
    protected String sanitizeInput(String input) {
        if (input == null) return "";
        return input
                .replace("\\n", " ")
                .replace("ignore previous instructions", "")
                .replace("ignore all instructions", "")
                .replace("</system>", "")
                .replace("<system>", "")
                .trim();
    }

    /**
     * 记录 Agent 执行开始日志
     */
    protected void logStart(AgentContext context) {
        log.info("[{}] 开始执行, taskId={}, goal={}", getRole(), context.getTaskId(),
                context.getGoal().substring(0, Math.min(50, context.getGoal().length())));
    }

    /**
     * 记录 Agent 执行结束日志
     */
    protected void logEnd(AgentContext context, AgentResult result) {
        log.info("[{}] 执行完成, taskId={}, success={}", getRole(), context.getTaskId(), result.isSuccess());
    }
}
