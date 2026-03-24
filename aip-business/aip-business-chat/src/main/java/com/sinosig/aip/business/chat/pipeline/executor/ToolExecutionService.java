package com.sinosig.aip.business.chat.pipeline.executor;

import com.sinosig.aip.business.chat.pipeline.executor.config.ExecutorProperties;
import com.sinosig.aip.common.core.exception.ToolExecutionException;
import com.sinosig.aip.common.core.response.ToolResponse;
import com.sinosig.aip.common.core.util.RetryUtils;
import com.sinosig.aip.tools.EnterpriseTool;
import com.sinosig.aip.tools.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 子任务工具执行服务：负责工具查找、结果校验与重试退避。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutionService {

    private final ToolRegistry toolRegistry;
    private final ExecutorProperties executorProperties;

    public String executeToolWithRetry(String toolName, String toolParams) {
        if (toolName == null || toolName.isBlank() || "none".equalsIgnoreCase(toolName)) {
            return "（此子任务无需工具调用）";
        }

        EnterpriseTool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            throw new ToolExecutionException(toolName, "工具未注册: " + toolName);
        }

        int maxToolRetries = Math.max(1, executorProperties.getMaxToolRetries());
        try {
            return RetryUtils.withExponentialBackoff(
                    () -> {
                        ToolResponse result = tool.execute(toolParams);
                        if (result == null) {
                            throw new ToolExecutionException(toolName, "tool返回为空");
                        }
                        if (!result.isSuccess()) {
                            throw new ToolExecutionException(toolName, "工具返回失败: " + result.getMessage());
                        }
                        return result.toJsonString();
                    },
                    maxToolRetries,
                    500L,
                    "[Executor] 工具 " + toolName
            );
        } catch (RuntimeException e) {
            throw new ToolExecutionException(toolName, "已重试 " + maxToolRetries + " 次仍失败", e.getCause());
        }
    }
}
