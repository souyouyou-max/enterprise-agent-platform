package com.sinosig.aip.app.ops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Ops/Agent 相关配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "aip.ops.agent")
public class OpsAgentProperties {

    private NacosMcp nacosMcp = new NacosMcp();

    @Data
    public static class NacosMcp {
        /**
         * 是否启用 Nacos MCP。
         */
        private boolean enabled = false;
        /**
         * MCP 网关地址，如 http://127.0.0.1:18080
         */
        private String baseUrl;
        /**
         * 可选鉴权 Token。
         */
        private String token;
        /**
         * 调用超时时间（毫秒）。
         */
        private int timeoutMs = 5000;
    }
}
