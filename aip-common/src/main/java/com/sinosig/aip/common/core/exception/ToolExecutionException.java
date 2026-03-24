package com.sinosig.aip.common.core.exception;

public class ToolExecutionException extends AgentException {

    private final String toolName;

    public ToolExecutionException(String toolName, String message) {
        super(500, "工具 [" + toolName + "] 执行失败: " + message);
        this.toolName = toolName;
    }

    public ToolExecutionException(String toolName, String message, Throwable cause) {
        super(500, "工具 [" + toolName + "] 执行失败: " + message, cause);
        this.toolName = toolName;
    }

    public String getToolName() {
        return toolName;
    }
}
