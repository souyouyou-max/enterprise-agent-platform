package com.sinosig.aip.common.ai.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EmbeddingResponse {

    /** 每个文本对应的向量 */
    private List<float[]> embeddings;

    /** 使用的模型 */
    private String model;

    /** 向量维度 */
    private int dimensions;
}
