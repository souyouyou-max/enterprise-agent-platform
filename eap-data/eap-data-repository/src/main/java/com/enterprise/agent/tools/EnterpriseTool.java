package com.enterprise.agent.tools;

import com.enterprise.agent.common.core.response.ToolResponse;

/**
 * 企业工具统一接口
 */
public interface EnterpriseTool {

    /**
     * 获取工具名称（唯一标识）
     */
    String getToolName();

    /**
     * 获取工具描述
     */
    String getDescription();

    /**
     * 执行工具
     *
     * @param params JSON 格式参数字符串
     * @return 工具执行结果（可包含原始 JSON）
     */
    ToolResponse execute(String params);
}
