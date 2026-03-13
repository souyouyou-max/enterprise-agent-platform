package com.enterprise.agent.common.ai.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class EmbeddingRequest {

    /** 要向量化的文本列表 */
    private List<String> texts;

    /** 模型名称（可选） */
    private String model;
}
