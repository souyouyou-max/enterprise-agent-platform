package com.sinosig.aip.business.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 使用 MessageWindowChatMemory + MessageChatMemoryAdvisor.builder() 管理对话记忆
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AipChatProperties.class)
public class ChatClientConfig {

    @Bean
    public ChatMemory chatMemory() {
        log.info("[ChatClientConfig] 初始化 chatMemory");
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(40)
                .build();
    }

    @Bean
    public ChatClient advisorChatClient(ChatModel chatModel, ChatMemory chatMemory) {
        log.info("[ChatClientConfig] 初始化 advisorChatClient，使用 MessageChatMemoryAdvisor");
        return ChatClient.builder(chatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),  // 记录完整请求/响应，便于调试
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }
}
