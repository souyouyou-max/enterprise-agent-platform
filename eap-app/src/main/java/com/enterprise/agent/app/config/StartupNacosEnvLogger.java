package com.enterprise.agent.app.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Properties;

/**
 * 在非常早期阶段打印 Nacos 相关最终生效的环境变量，便于定位“控制台有配置但应用读到 null”的问题。
 * 这里同时使用 System.err，避免日志系统尚未完全初始化时丢失输出。
 */
public class StartupNacosEnvLogger implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String[] keys = {
                // .env / 系统环境变量（用于确认占位符解析是否拿到了值）
                "DB_HOST",
                "DB_PORT",
                "NACOS_ADDR",
                "spring.cloud.nacos.config.server-addr",
                "spring.cloud.nacos.config.namespace",
                "spring.cloud.nacos.config.group",
                "spring.cloud.nacos.config.username",
                "spring.datasource.url",
                "spring.datasource.driver-class-name",
                "spring.datasource.username",
                // password 不打印
                "spring.config.import"
        };

        StringBuilder sb = new StringBuilder(256);
        sb.append("[StartupNacosEnv] effective properties:\n");
        for (String k : keys) {
            sb.append("  ").append(k).append(" = ").append(nullToEmpty(environment.getProperty(k))).append("\n");
        }

        // 额外打印：shared-configs/group 在 properties 中不一定落到 spring.cloud.nacos.config.group，
        // 我们这里按当前 bootstrap.yml 的默认组策略做兜底。
        String defaultGroup = nullToEmpty(environment.getProperty("spring.cloud.nacos.config.group", "DEFAULT_GROUP"));
        sb.append("  [fallback] sharedConfigGroup = ").append(defaultGroup).append("\n");

        // IntelliJ 运行时有时标准错误流不会出现在控制台捕获里，这里同时输出到 stdout。
        System.out.print(sb);

        // 主动验证：在 Spring 容器初始化失败之前，确认 Nacos 是否能读取 public common.yml
        tryProbeNacos(environment);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private void tryProbeNacos(ConfigurableEnvironment environment) {
        String serverAddr = environment.getProperty("spring.cloud.nacos.config.server-addr");
        String namespace = environment.getProperty("spring.cloud.nacos.config.namespace");
        String username = environment.getProperty("spring.cloud.nacos.config.username");
        String password = environment.getProperty("spring.cloud.nacos.config.password");
        String group = nullToEmpty(environment.getProperty("spring.cloud.nacos.config.group", "DEFAULT_GROUP"));

        if (serverAddr == null || serverAddr.isBlank()) {
            System.out.println("[StartupNacosProbe] skip: server-addr is blank");
            return;
        }

        Properties nacosProps = new Properties();
        nacosProps.setProperty(PropertyKeyConst.SERVER_ADDR, serverAddr);
        if (namespace != null && !namespace.isBlank()) {
            nacosProps.setProperty(PropertyKeyConst.NAMESPACE, namespace);
        }
        if (username != null && !username.isBlank()) {
            nacosProps.setProperty(PropertyKeyConst.USERNAME, username);
        }
        if (password != null && !password.isBlank()) {
            nacosProps.setProperty(PropertyKeyConst.PASSWORD, password);
        }

        try {
            ConfigService configService = NacosFactory.createConfigService(nacosProps);
            probeOne(configService, "common.yml", group);
            probeOne(configService, "eap-app.yml", group);
        } catch (Exception e) {
            System.out.println("[StartupNacosProbe] failed to create ConfigService: " + e.getClass().getSimpleName() + ":" + e.getMessage());
        }
    }

    private void probeOne(ConfigService configService, String dataId, String group) {
        try {
            String content = configService.getConfig(dataId, group, 3000);
            System.out.println("[StartupNacosProbe] getConfig dataId=" + dataId + ", group=" + group + " -> " + (content == null ? "null" : content));
            int len = content == null ? -1 : content.length();
            System.out.println("[StartupNacosProbe] getConfig dataId=" + dataId + ", group=" + group + " -> length=" + len);
        } catch (NacosException e) {
            System.out.println("[StartupNacosProbe] getConfig dataId=" + dataId + ", group=" + group + " failed: " + e.getErrMsg());
        } catch (Exception e) {
            System.out.println("[StartupNacosProbe] getConfig dataId=" + dataId + ", group=" + group + " failed: " + e.getClass().getSimpleName() + ":" + e.getMessage());
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

