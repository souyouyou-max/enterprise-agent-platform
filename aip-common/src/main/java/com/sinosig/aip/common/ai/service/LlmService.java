package com.sinosig.aip.common.ai.service;

import com.sinosig.aip.common.ai.model.LlmRequest;
import com.sinosig.aip.common.ai.model.LlmResponse;

/**
 * LLM 服务统一接口
 * 支持 OpenAI / Claude / Ollama 等多种后端实现
 */
public interface LlmService {

    /**
     * 同步调用 LLM
     *
     * @param request LLM 请求
     * @return LLM 响应
     */
    LlmResponse chat(LlmRequest request);

    /**
     * 简便调用：仅传入用户消息（使用默认系统提示）
     *
     * @param userMessage 用户消息
     * @return LLM 输出文本
     */
    default String simpleChat(String userMessage) {
        LlmRequest request = LlmRequest.builder()
                .userMessage(userMessage)
                .build();
        return chat(request).getContent();
    }

    /**
     * 带系统提示的调用
     *
     * @param systemPrompt 系统提示
     * @param userMessage  用户消息
     * @return LLM 输出文本
     */
    default String chatWithSystem(String systemPrompt, String userMessage) {
        LlmRequest request = LlmRequest.builder()
                .systemPrompt(systemPrompt)
                .userMessage(userMessage)
                .build();
        return chat(request).getContent();
    }

    /**
     * 获取当前使用的 LLM Provider 名称
     */
    String getProviderName();
}
