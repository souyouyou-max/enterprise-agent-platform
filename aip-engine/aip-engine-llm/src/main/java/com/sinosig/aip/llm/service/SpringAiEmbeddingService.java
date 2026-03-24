package com.sinosig.aip.llm.service;

import com.sinosig.aip.common.ai.model.EmbeddingRequest;
import com.sinosig.aip.common.ai.model.EmbeddingResponse;
import com.sinosig.aip.common.ai.service.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SpringAI 向量化服务实现
 */
@Slf4j
@Service
public class SpringAiEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public SpringAiEmbeddingService(@Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public EmbeddingResponse embed(EmbeddingRequest request) {
        log.debug("[EmbeddingService] 向量化 {} 条文本", request.getTexts().size());
        try {
            List<float[]> embeddings = request.getTexts().stream()
                    .map(text -> {
                        float[] vector = embeddingModel.embed(text);
                        return vector;
                    })
                    .collect(Collectors.toList());

            int dimensions = embeddings.isEmpty() ? 0 : embeddings.get(0).length;

            return EmbeddingResponse.builder()
                    .embeddings(embeddings)
                    .model("embedding")
                    .dimensions(dimensions)
                    .build();

        } catch (Exception e) {
            log.error("[EmbeddingService] 向量化失败: {}", e.getMessage(), e);
            throw new RuntimeException("向量化失败: " + e.getMessage(), e);
        }
    }
}
