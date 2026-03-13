package com.enterprise.agent.knowledge.service;

import com.enterprise.agent.common.ai.model.EmbeddingRequest;
import com.enterprise.agent.common.ai.model.EmbeddingResponse;
import com.enterprise.agent.common.ai.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量化服务实现：调用 Spring AI EmbeddingModel 将文本转为浮点向量
 * 具体后端（OpenAI text-embedding-3-small / Ollama nomic-embed-text 等）
 * 由 eap-llm-service 通过 Spring AI Starter 自动配置注入
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    private final EmbeddingModel embeddingModel;

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        List<float[]> embeddings = new ArrayList<>();
        for (String text : request.getTexts()) {
            float[] vector = embeddingModel.embed(text);
            embeddings.add(vector);
            log.debug("文本向量化完成，维度: {}", vector.length);
        }
        int dimensions = embeddings.isEmpty() ? 0 : embeddings.get(0).length;
        return EmbeddingResponse.builder()
                .embeddings(embeddings)
                .dimensions(dimensions)
                .build();
    }
}
