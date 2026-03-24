package com.sinosig.aip.business.chat.pipeline.executor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Chat 侧同步 Pipeline（Executor 阶段）执行参数配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "aip.business.chat.pipeline.executor")
public class ExecutorProperties {

    /**
     * 工具调用最大重试次数。
     */
    private int maxToolRetries = 3;

    /**
     * LLM 分析最大重试次数。
     */
    private int maxLlmRetries = 2;

    /**
     * 子任务成功率阈值（百分比，0-100）。
     * 完成的子任务占比低于此值时，ExecutorAgent 返回失败，触发 Pipeline 终止。
     * 默认 50，即至少一半子任务成功才算通过。
     * 可配置为 100 以要求所有子任务必须成功。
     */
    private int successThresholdPercent = 50;
}
