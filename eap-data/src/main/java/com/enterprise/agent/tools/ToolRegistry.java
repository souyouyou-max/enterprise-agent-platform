package com.enterprise.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 企业工具注册中心 - 自动注册所有 EnterpriseTool Bean
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, EnterpriseTool> toolMap;

    public ToolRegistry(List<EnterpriseTool> tools) {
        this.toolMap = tools.stream()
                .collect(Collectors.toMap(EnterpriseTool::getToolName, Function.identity()));
        log.info("ToolRegistry 已注册 {} 个工具: {}", tools.size(),
                tools.stream().map(EnterpriseTool::getToolName).collect(Collectors.joining(", ")));
    }

    public EnterpriseTool getTool(String toolName) {
        return toolMap.get(toolName);
    }

    public Map<String, String> listTools() {
        return toolMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getDescription()));
    }
}
