package com.sinosig.aip.core.agent;

import com.sinosig.aip.common.ai.service.LlmService;
import com.sinosig.aip.common.core.enums.AgentRole;
import com.sinosig.aip.core.context.AgentContext;
import com.sinosig.aip.core.context.AgentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/**
 * BaseAgent - 所有 Agent 的抽象基类
 * 封装 ChatClient 构建、系统提示注入、Prompt 安全清洗
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
        String goal = context.getGoal();
        String goalPreview = (goal != null) ? goal.substring(0, Math.min(50, goal.length())) : "(无目标)";
        log.info("[{}] 开始执行, taskId={}, goal={}", getRole(), context.getTaskId(), goalPreview);
    }

    /**
     * 记录 Agent 执行结束日志
     */
    protected void logEnd(AgentContext context, AgentResult result) {
        log.info("[{}] 执行完成, taskId={}, success={}", getRole(), context.getTaskId(), result.isSuccess());
    }
}
