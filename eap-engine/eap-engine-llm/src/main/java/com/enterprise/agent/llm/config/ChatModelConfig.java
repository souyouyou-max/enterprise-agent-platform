package com.enterprise.agent.llm.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 根据 eap.llm.provider 配置动态选择 ChatModel。
 * <p>
 * 支持的 provider（需在 pom.xml 引入对应 spring-ai-starter）：
 * <ul>
 *   <li>openai  — spring-ai-starter-model-openai（当前默认）</li>
 *   <li>claude  — spring-ai-starter-model-anthropic</li>
 *   <li>ollama  — spring-ai-starter-model-ollama</li>
 * </ul>
 * 切换方式：修改 eap.llm.provider 配置项，并确保对应的 api-key / base-url 已注入。
 * <p>
 * 设计说明：通过 ApplicationContext 按 Bean 名懒查找，避免对可选 starter 中的类产生
 * 硬引用（否则未引入对应 starter 时会在 Spring 字段反射阶段抛 NoClassDefFoundError）。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ChatModelConfig {

    private final LlmProviderConfig config;
    private final ApplicationContext applicationContext;

    /** openai provider 对应的 Spring AI Bean 名称 */
    private static final String OPENAI_BEAN   = "openAiChatModel";
    /** claude provider 对应的 Spring AI Bean 名称 */
    private static final String CLAUDE_BEAN   = "anthropicChatModel";
    /** ollama provider 对应的 Spring AI Bean 名称 */
    private static final String OLLAMA_BEAN   = "ollamaChatModel";

    @Bean
    @Primary
    public ChatModel primaryChatModel() {
        String provider = config.getProvider();
        log.info("[LLM] 配置的 Provider: {}", provider);

        return switch (provider.toLowerCase()) {
            case "openai" -> {
                log.info("[LLM] 使用 OpenAI 模型: {}", config.getOpenai().getModel());
                yield resolveBean(OPENAI_BEAN, "openai",
                        "请检查 spring.ai.openai.api-key 是否已通过 Nacos 或环境变量注入。");
            }
            case "claude" -> {
                log.info("[LLM] 使用 Claude 模型: {}", config.getClaude().getModel());
                yield resolveBean(CLAUDE_BEAN, "claude",
                        "请在 eap-app/pom.xml 引入 spring-ai-starter-model-anthropic，" +
                        "并配置 spring.ai.anthropic.api-key。");
            }
            case "ollama" -> {
                log.info("[LLM] 使用 Ollama 模型: {}", config.getOllama().getModel());
                yield resolveBean(OLLAMA_BEAN, "ollama",
                        "请在 eap-app/pom.xml 引入 spring-ai-starter-model-ollama，" +
                        "并配置 spring.ai.ollama.base-url。");
            }
            default -> throw new IllegalArgumentException(
                    "[LLM] 不支持的 provider: '" + provider + "'，可选值: openai / claude / ollama");
        };
    }

    /**
     * 从 ApplicationContext 中按名查找 ChatModel Bean。
     * 使用 String beanName 而非具体类型，避免对可选 starter 中的类产生字节码级硬引用。
     *
     * @param beanName    Spring AI 自动装配产生的 Bean 名称
     * @param provider    provider 名称（仅用于错误日志）
     * @param hint        对使用者的配置提示
     * @return ChatModel 实例
     * @throws IllegalStateException 若对应 Bean 未初始化（starter 缺失或 api-key 未配置）
     */
    private ChatModel resolveBean(String beanName, String provider, String hint) {
        try {
            ChatModel model = (ChatModel) applicationContext.getBean(beanName);
            log.info("[LLM] Bean '{}' 获取成功，provider={}", beanName, provider);
            return model;
        } catch (NoSuchBeanDefinitionException e) {
            throw new IllegalStateException(
                    String.format("[LLM] provider=%s，但 Bean '%s' 未初始化。%s", provider, beanName, hint), e);
        }
    }
}
