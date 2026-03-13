package com.enterprise.agent.common;

import com.enterprise.agent.common.exception.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * eap-common 自动配置：向 Spring 容器注册统一全局异常处理器
 */
@AutoConfiguration
@Import(GlobalExceptionHandler.class)
public class EapCommonAutoConfiguration {
}
