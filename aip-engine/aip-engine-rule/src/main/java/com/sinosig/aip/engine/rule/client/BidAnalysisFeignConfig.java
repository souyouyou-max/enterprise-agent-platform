package com.sinosig.aip.engine.rule.client;

import feign.Request;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * BidAnalysisClient 专用 Feign 超时配置
 * OCR 处理扫描件耗时较长，需要较大的 readTimeout
 */
@Configuration
public class BidAnalysisFeignConfig {

    @Value("${aip.audit.collusive.python.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${aip.audit.collusive.python.read-timeout-ms:120000}")
    private int readTimeoutMs;

    @Bean
    public Request.Options bidAnalysisRequestOptions() {
        return new Request.Options(connectTimeoutMs, TimeUnit.MILLISECONDS,
                readTimeoutMs, TimeUnit.MILLISECONDS, true);
    }
}
