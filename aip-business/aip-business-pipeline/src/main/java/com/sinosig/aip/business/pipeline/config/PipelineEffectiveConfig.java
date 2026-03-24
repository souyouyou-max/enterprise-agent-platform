package com.sinosig.aip.business.pipeline.config;

import java.util.List;

/**
 * 合并全局 {@link AipPipelineProperties} 与批次 {@code extra_info} 后的有效流水线参数。
 * {@code multimodalTemplateKeys}：extra_info.multimodalPromptKeys（优先）或单键 multimodalPromptKey &gt; 全局。
 */
public record PipelineEffectiveConfig(
        boolean ocrEnabled,
        boolean analysisEnabled,
        boolean compareEnabled,
        double failToleranceRatio,
        /**
         * 单次正言 img2text 请求最多携带的分片图数量；多页时会按此大小分多次调用。
         * 例如=1 且共 4 页则调用 4 次。
         */
        int maxImagesPerFile,
        /** 语义分析最多处理的页数（按分片顺序截断）；与 {@link #maxImagesPerFile} 配合决定总调用次数 */
        int maxTotalAnalysisPages,
        List<String> multimodalTemplateKeys
) {}
