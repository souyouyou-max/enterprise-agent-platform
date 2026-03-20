package com.enterprise.agent.tools.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * HTTP 客户端配置。
 * 为企业工具（如 DazhiOcrTool）提供统一的 RestTemplate Bean，并按配置设置超时。
 * 内置 {@link Base64TruncatingInterceptor} 拦截器，在 DEBUG 日志中将 base64 字段截断，
 * 避免大图片数据撑满日志输出。
 */
@Configuration
public class HttpClientConfig {

    /** 连接超时，单位毫秒，默认 5 秒 */
    @Value("${eap.http.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    /** 读取超时，复用大智部 OCR 的超时配置，默认 20 秒 */
    @Value("${eap.tools.dazhi.ocr.timeout-ms:20000}")
    private int readTimeoutMs;

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.setInterceptors(List.of(new Base64TruncatingInterceptor()));
        return restTemplate;
    }

    /**
     * RestTemplate 请求拦截器：仅在 DEBUG 级别有效，将请求体中 base64 长字段截断后输出日志，
     * 不影响实际发送的报文内容。
     */
    @Slf4j
    static class Base64TruncatingInterceptor implements ClientHttpRequestInterceptor {

        /** 匹配 JSON 中 base64 长字段：picContent / imageBase64 / base64，截取前 80 字符 */
        private static final Pattern BASE64_PATTERN =
                Pattern.compile("(\"(?:picContent|imageBase64|base64)\"\\s*:\\s*\")([A-Za-z0-9+/=]{80})[A-Za-z0-9+/=]*(\"?)");

        private static final int LOG_BODY_MAX = 2000;

        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            if (log.isDebugEnabled()) {
                String raw = new String(body, StandardCharsets.UTF_8);
                String truncated = BASE64_PATTERN.matcher(raw).replaceAll("$1$2...(truncated)$3");
                String preview = truncated.length() > LOG_BODY_MAX
                        ? truncated.substring(0, LOG_BODY_MAX) + "...(body truncated)"
                        : truncated;
                log.debug("[RestTemplate] --> {} {}  body={}", request.getMethod(), request.getURI(), preview);
            }
            return execution.execute(request, body);
        }
    }
}
