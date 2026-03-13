package com.enterprise.agent.service.agent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 定时任务配置
 * 开启 Spring @Scheduled 支持，用于规则SQL分析引擎的定时驱动。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
