package com.sinosig.aip.business.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * AI 对话层配置（application.yml / Nacos：{@code aip.pipeline.chat}）。
 *
 * <p>与 OCR 流水线配置（{@code aip.pipeline.*}，见 aip-business-pipeline 模块）相互独立，
 * 支持 Nacos 动态刷新，无需重启服务。
 */
@Data
@ConfigurationProperties(prefix = "aip.pipeline.chat")
public class AipChatProperties {

    /**
     * 用于判断用户消息是否需要调用 Agent 工具的关键词列表。
     * 命中任意一个关键词（大小写不敏感）时，{@code shouldUseTools()} 返回 {@code true}。
     */
    private List<String> orgToolKeywords = List.of(
            "orgcode", "机构", "风控", "风险", "监测", "监控",
            "线索", "疑点", "告警", "analysis", "analyze", "monitor", "clue"
    );
}
