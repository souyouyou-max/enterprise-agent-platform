package com.sinosig.aip.common.ai.service;

import com.sinosig.aip.common.ai.model.EmbeddingRequest;
import com.sinosig.aip.common.ai.model.EmbeddingResponse;

import java.util.List;

/**
 * 向量化服务接口
 */
public interface EmbeddingService {

    /**
     * 批量文本向量化
     *
     * @param request 向量化请求
     * @return 向量化结果
     */
    EmbeddingResponse embed(EmbeddingRequest request);

    /**
     * 单文本向量化
     *
     * @param text 文本
     * @return 浮点数向量
     */
    default float[] embedSingle(String text) {
        EmbeddingRequest request = EmbeddingRequest.builder()
                .texts(List.of(text))
                .build();
        EmbeddingResponse response = embed(request);
        return response.getEmbeddings().isEmpty() ? new float[0] : response.getEmbeddings().get(0);
    }
}
