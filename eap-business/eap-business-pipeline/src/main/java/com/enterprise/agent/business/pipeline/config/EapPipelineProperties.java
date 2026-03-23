package com.enterprise.agent.business.pipeline.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OCR 流水线配置（application.yml / Nacos：{@code eap.pipeline}）。
 *
 * <p>阶段<strong>顺序固定</strong>为：OCR → 语义分析（正言）→ 相似度对比；不可调换顺序，
 * 仅通过各阶段 {@code enabled} 开关跳过中间或末尾阶段。
 *
 * <p>多模态话术：{@code analysis.multimodal-prompt-key} 指向 {@code prompts.templates} 中的键。
 *
 * <p>按批次覆盖：在 {@code submit-batch} 的 {@code extra_info} JSON 中填写可选字段（见 {@link PipelineEnv}），
 * 与 {@link PipelineEffectiveConfigResolver} 合并，无需在 YAML 再维护多套 profile。
 */
@Data
@ConfigurationProperties(prefix = "eap.pipeline")
public class EapPipelineProperties {

    /**
     * OCR 失败数 / 总数 超过该比例则终止批次（0~1）。
     */
    private double failToleranceRatio = 0.5;

    private Ocr ocr = new Ocr();
    private Analysis analysis = new Analysis();
    private Compare compare = new Compare();
    private Scheduler scheduler = new Scheduler();
    private Prompts prompts = new Prompts();

    @Data
    public static class Ocr {
        /**
         * 为 false 时提交批次后不自动触发 OCR，需手动/定时补偿触发（默认 true）。
         */
        private boolean enabled = true;
    }

    @Data
    public static class Analysis {
        /**
         * 为 false 时跳过正言多模态分析，OCR 完成后直接进入对比（若开启）或结束。
         */
        private boolean enabled = true;
        /**
         * 单次正言 img2text 请求最多携带的分片图数；多页时按此大小分多次调用。
         * 设为 1 表示每页单独一次大模型调用。
         */
        private int maxImagesPerFile = 8;
        /**
         * 语义分析最多处理的页数（按分片顺序）；防止极长 PDF 打爆配额。与 maxImagesPerFile 相乘影响总请求量。
         */
        private int maxTotalAnalysisPages = 100;
        /**
         * 正言 img2text 的 {@code text} 使用哪套模板，对应 {@link Prompts#templates} 的 key（默认 {@code default}）。
         * 切换场景（投标筛查、合同合规等）时改此键即可，无需改代码。
         * <p>
         * 运行时可由环境变量 {@link PipelineEnv#PIPELINE_ANALYSIS_MULTIMODAL_PROMPT_KEY} 在 YAML 中注入
         * （见 eap-app {@code application.yml} 的 {@code multimodal-prompt-key: ${...}}），绑定到本字段。
         */
        private String multimodalPromptKey = "default";
    }

    @Data
    public static class Prompts {
        /**
         * 模板键 → 发给正言的完整指令文本。未配置的键在解析时回退到内置默认话术。
         * 若需与入库字段一致，自定义模板仍应要求模型返回与默认相同的 JSON 字段结构。
         */
        private Map<String, String> templates = new LinkedHashMap<>();
    }

    @Data
    public static class Compare {
        /**
         * 为 false 时跳过 Python 相似度对比，语义分析（若开启）结束后批次直接 DONE。
         */
        private boolean enabled = true;
    }

    @Data
    public static class Scheduler {
        private boolean enabled = true;
        private long intervalMs = 60_000L;
        private int pendingStaleSeconds = 120;
        private int processingStaleSeconds = 1800;
        private int doneStaleSeconds = 300;
    }

}
