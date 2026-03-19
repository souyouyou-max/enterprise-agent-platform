package com.enterprise.agent.llm.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 根据 eap.llm.provider 配置动态选择 ChatModel
 * 解决多个 ChatModel Bean 冲突问题
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ChatModelConfig {

    private final LlmProviderConfig config;

    @Autowired(required = false)
    private OpenAiChatModel openAiChatModel;

    @Bean
    @Primary
    public ChatModel primaryChatModel() {
        String provider = config.getProvider();
        log.info("[LLM] 当前 Provider: {}", provider);

        if (openAiChatModel == null) {
            throw new IllegalStateException("OpenAiChatModel 未配置，请检查 spring.ai.openai 配置");
        }

        log.info("[LLM] 使用 OpenAI 模型");
        return openAiChatModel;
    }
}
