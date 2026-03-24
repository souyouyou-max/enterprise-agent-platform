package com.sinosig.aip.llm.service;

import com.sinosig.aip.common.ai.model.LlmRequest;
import com.sinosig.aip.common.ai.model.LlmResponse;
import com.sinosig.aip.common.ai.service.LlmService;
import com.sinosig.aip.common.core.exception.LlmException;
import com.sinosig.aip.llm.config.LlmProviderConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * SpringAiLlmService - 基于 Spring AI 的 LLM 服务实现
 * 支持 OpenAI / Claude / Ollama 多模型热切换
 * 使用 Redis 缓存 LLM 响应（TTL 1小时）
 */
@Slf4j
@Service
public class SpringAiLlmService implements LlmService {

    private final ChatModel chatModel;
    private final LlmProviderConfig config;

    public SpringAiLlmService(ChatModel chatModel, LlmProviderConfig config) {
        this.chatModel = chatModel;
        this.config = config;
        log.info("[LlmService] 初始化完成，当前 Provider: {}", config.getProvider());
    }

    @Override
    @Cacheable(value = "llm-responses", key = "#root.target.cacheKey(#request)", unless = "!#root.target.config.cacheEnabled")
    public LlmResponse chat(LlmRequest request) {
        log.info("[LlmService] 调用 LLM, provider={}, cacheEnabled={}",
                config.getProvider(), config.isCacheEnabled());
        try {
            List<Message> messages = buildMessages(request);
            Prompt prompt = new Prompt(messages);
            var response = chatModel.call(prompt);

            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw new LlmException("LLM 返回空结果（response/result/output 为 null）");
            }

            String content = response.getResult().getOutput().getText();
            if (content == null) {
                content = "";
            }
            log.debug("[LlmService] LLM 响应长度: {} chars", content.length());

            if (content.isBlank()) {
                throw new LlmException("LLM 返回空响应（可能是上游接口返回空/格式不兼容/路径配置错误）");
            }

            return LlmResponse.builder()
                    .content(content)
                    .model(config.getProvider())
                    .cached(false)
                    .finishReason(response.getResult().getMetadata() == null ? null : response.getResult().getMetadata().getFinishReason())
                    .build();

        } catch (Exception e) {
            log.error("[LlmService] LLM 调用失败: {}", e.getMessage(), e);
            throw new LlmException("LLM 调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return config.getProvider();
    }

    /**
     * 生成缓存 Key（基于请求内容的 MD5）
     */
    public String cacheKey(LlmRequest request) {
        String raw = config.getProvider() + "|" +
                (request.getSystemPrompt() != null ? request.getSystemPrompt() : "") + "|" +
                request.getUserMessage();
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private List<Message> buildMessages(LlmRequest request) {
        List<Message> messages = new ArrayList<>();

        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.add(new SystemMessage(request.getSystemPrompt()));
        }

        // 添加历史对话
        if (request.getHistory() != null) {
            for (LlmRequest.ChatMessage histMsg : request.getHistory()) {
                if ("user".equals(histMsg.getRole())) {
                    messages.add(new UserMessage(histMsg.getContent()));
                } else if ("assistant".equals(histMsg.getRole())) {
                    messages.add(new AssistantMessage(histMsg.getContent()));
                }
            }
        }

        messages.add(new UserMessage(request.getUserMessage()));
        return messages;
    }
}
