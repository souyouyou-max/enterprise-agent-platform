package org.springframework.boot.autoconfigure.web.reactive.function.client;

import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * 兼容占位：部分三方自动装配仍引用该类（旧包名）。
 *
 * 注意：在 Spring Boot 4.0.3 的最小 Web（非 WebFlux）依赖组合下，
 * 该类及其新版替代实现都可能不在类路径中；因此这里只提供占位类，
 * 避免注解解析阶段因 ClassNotFound 直接失败。
 */
@AutoConfiguration
public class WebClientAutoConfiguration {
}

