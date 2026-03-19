package org.springframework.boot.autoconfigure.web.client;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot 4 将 RestClientAutoConfiguration 移到了 {@code org.springframework.boot.restclient.autoconfigure}.
 * 这里提供一个兼容桥接，供仍引用旧包名的三方自动装配使用（例如部分 Spring AI 里程碑版本）。
 */
@AutoConfiguration
@Import(org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration.class)
public class RestClientAutoConfiguration {
}

