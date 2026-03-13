package com.enterprise.agent.llm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LLM Provider 配置（支持热切换）
 */
@Data
@Component
@ConfigurationProperties(prefix = "eap.llm")
public class LlmProviderConfig {

    /** 当前使用的 LLM Provider: openai / claude / ollama */
    private String provider = "openai";

    /** 默认模型名称（各 provider 独立设置） */
    private String model = "";

    /** 请求超时（秒） */
    private int timeoutSeconds = 60;

    /** 是否启用 Redis 缓存 */
    private boolean cacheEnabled = true;

    /** 缓存 TTL（秒），默认 3600 = 1小时 */
    private int cacheTtlSeconds = 3600;

    /** OpenAI 配置 */
    private OpenAiConfig openai = new OpenAiConfig();

    /** Claude/Anthropic 配置 */
    private ClaudeConfig claude = new ClaudeConfig();

    /** Ollama 配置 */
    private OllamaConfig ollama = new OllamaConfig();

    @Data
    public static class OpenAiConfig {
        private String model = "gpt-4o";
    }

    @Data
    public static class ClaudeConfig {
        private String model = "claude-sonnet-4-6";
    }

    @Data
    public static class OllamaConfig {
        private String model = "llama3.1";
        private String baseUrl = "http://localhost:11434";
    }
}
