package com.enterprise.agent.app.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * IntelliJ 直接 Run/Debug 时，进程未必会自动读取工作区根目录的 `.env`。
 * 该处理器会向 Spring Environment 注入 `.env` 中的键值对，
 * 让占位符（如 ${DB_HOST} / ${NACOS_USERNAME}）能够正常解析。
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final int MAX_SEARCH_LEVELS = 8;

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> dotenv = loadDotenvFromWorkspaceRoot();
        if (dotenv.isEmpty()) {
            return;
        }
        // 放在最前面，优先级最高；本地 .env 用来覆盖系统环境变量/其它配置。
        environment.getPropertySources().addFirst(new MapPropertySource("dotenv", dotenv));
    }

    private static Map<String, Object> loadDotenvFromWorkspaceRoot() {
        Path base = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (int i = 0; i < MAX_SEARCH_LEVELS && base != null; i++) {
            Path candidate = base.resolve(".env");
            if (Files.isRegularFile(candidate)) {
                return parseDotenvFile(candidate);
            }
            base = base.getParent();
        }
        return new HashMap<>();
    }

    private static Map<String, Object> parseDotenvFile(Path path) {
        Map<String, Object> map = new HashMap<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return new HashMap<>();
        }

        for (String rawLine : lines) {
            if (rawLine == null) {
                continue;
            }
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            // 支持形如：export KEY=VALUE
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }

            int eqIdx = line.indexOf('=');
            if (eqIdx <= 0) {
                continue;
            }

            String key = line.substring(0, eqIdx).trim();
            if (key.isEmpty()) {
                continue;
            }

            String value = line.substring(eqIdx + 1).trim();
            value = stripSurroundingQuotes(value);

            map.put(key, value);
        }
        return map;
    }

    private static String stripSurroundingQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    @Override
    public int getOrder() {
        // 比 StartupNacosEnvLogger 的 HIGHEST_PRECEDENCE 略低，避免打印阶段看不到值。
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}

