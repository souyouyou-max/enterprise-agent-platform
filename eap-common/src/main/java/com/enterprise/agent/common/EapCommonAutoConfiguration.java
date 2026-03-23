package com.enterprise.agent.common;

import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * eap-common 自动配置入口（保留扩展点）。
 * GlobalExceptionHandler 已迁移至 eap-app 的 @ComponentScan 扫描范围，无需在此 @Import。
 */
@AutoConfiguration
public class EapCommonAutoConfiguration {
}
