package com.sinosig.aip.business.pipeline.config;

/**
 * 流水线相关<strong>环境变量名</strong>（与 aip-app {@code application.yml} 中 {@code ${...}} 占位符一致）。
 * <p>
 * 例如 {@link #PIPELINE_ANALYSIS_MULTIMODAL_PROMPT_KEY} 在 YAML 中写作：
 * {@code aip.pipeline.analysis.multimodal-prompt-key: ${PIPELINE_ANALYSIS_MULTIMODAL_PROMPT_KEY:default}}
 * 启动时由 Spring 解析进 {@link AipPipelineProperties.Analysis#setMultimodalPromptKey(String)}，
 * 业务代码只读 {@link AipPipelineProperties#getAnalysis()}，不直接读取该环境变量名。
 */
public final class PipelineEnv {

    private PipelineEnv() {
    }

    /**
     * 覆盖 {@code aip.pipeline.analysis.multimodal-prompt-key}（如 {@code default}、{@code bid_screening}）。
     */
    public static final String PIPELINE_ANALYSIS_MULTIMODAL_PROMPT_KEY = "PIPELINE_ANALYSIS_MULTIMODAL_PROMPT_KEY";

    /**
     * 注入 {@code aip.pipeline.analysis.max-total-analysis-pages}（语义分析最多处理多少页分片）。
     */
    public static final String PIPELINE_MAX_TOTAL_ANALYSIS_PAGES = "PIPELINE_MAX_TOTAL_ANALYSIS_PAGES";

    /**
     * 批次 {@code extra_info} 中<strong>可选</strong>覆盖话术模板 key（对应 {@code aip.pipeline.prompts.templates} 的键名）。
     * <p><strong>默认</strong>：不传此字段时，使用配置文件 {@code aip.pipeline.analysis.multimodal-prompt-key}
     * （可由环境变量 {@link #PIPELINE_ANALYSIS_MULTIMODAL_PROMPT_KEY} 注入）。
     * 仅当单次上传需要与全局默认不同的模板时，再在 extra 里传本字段。
     */
    public static final String EXTRA_INFO_JSON_MULTIMODAL_PROMPT_KEY = "multimodalPromptKey";

    /**
     * 批次 {@code extra_info} 中多套话术模板 key 数组（对应 {@code aip.pipeline.prompts.templates}），
     * 与 {@link #EXTRA_INFO_JSON_MULTIMODAL_PROMPT_KEY} 二选一优先：若数组非空则按多模板合并为一次多模态调用。
     */
    public static final String EXTRA_INFO_JSON_MULTIMODAL_PROMPT_KEYS = "multimodalPromptKeys";

    /** 是否自动跑 OCR（缺省跟全局 {@code aip.pipeline.ocr.enabled}） */
    public static final String EXTRA_INFO_JSON_OCR_ENABLED = "ocrEnabled";

    /** 是否跑语义分析 */
    public static final String EXTRA_INFO_JSON_ANALYSIS_ENABLED = "analysisEnabled";

    /** 是否跑相似度对比 */
    public static final String EXTRA_INFO_JSON_COMPARE_ENABLED = "compareEnabled";

    /** 覆盖单次 img2text 请求最多带几张分片图（再大易超请求体） */
    public static final String EXTRA_INFO_JSON_MAX_IMAGES_PER_FILE = "maxImagesPerFile";

    /** 覆盖语义分析最多处理多少页（分片） */
    public static final String EXTRA_INFO_JSON_MAX_TOTAL_ANALYSIS_PAGES = "maxTotalAnalysisPages";

    /** 覆盖 OCR 失败容忍比例（0~1） */
    public static final String EXTRA_INFO_JSON_FAIL_TOLERANCE_RATIO = "failToleranceRatio";
}
