package com.enterprise.agent.app.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 在非常早期阶段打印 Nacos 相关最终生效的环境变量，便于定位“控制台有配置但应用读到 null”的问题。
 * 这里同时使用 System.err，避免日志系统尚未完全初始化时丢失输出。
 */
public class StartupNacosEnvLogger implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String[] keys = {
                "spring.cloud.nacos.config.server-addr",
                "spring.cloud.nacos.config.namespace",
                "spring.cloud.nacos.config.group",
                "spring.cloud.nacos.config.username",
                // password 不打印
                "spring.config.import"
        };

        StringBuilder sb = new StringBuilder(256);
        sb.append("[StartupNacosEnv] effective properties:\n");
        for (String k : keys) {
            sb.append("  ").append(k).append(" = ").append(nullToEmpty(environment.getProperty(k))).append("\n");
        }

        System.err.print(sb);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

