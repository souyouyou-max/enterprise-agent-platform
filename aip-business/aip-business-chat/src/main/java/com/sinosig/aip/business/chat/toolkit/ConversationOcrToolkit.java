package com.sinosig.aip.business.chat.toolkit;

import com.sinosig.aip.common.core.response.ToolResponse;
import com.sinosig.aip.tools.EnterpriseTool;
import com.sinosig.aip.tools.ToolRegistry;
import com.sinosig.aip.tools.impl.ZhengyanPlatformTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * ConversationOcrToolkit - 面向主对话的图文/通用OCR工具集。
 *
 * 为 InteractionCenterAgent 提供两个工具：
 * 1) img2TextForChat       → 走正言 img2text，多图+理解+总结
 * 2) dazhiOcrGeneralForChat → 走大智部通用OCR，更偏证件/票据结构化识别
 *
 * 由 LLM 根据用户话术自动选择合适工具。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationOcrToolkit {

    private static final String DAZHI_OCR_TOOL_NAME = "callDazhiOcrGeneral";

    private final ZhengyanPlatformTool zhengyanPlatformTool;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    @Tool(description = "正言图文理解（适合需要对图片或文档内容做总结、提炼要点、回答问题等语义理解场景）。" +
            "params为JSON字符串，通常包含text(提问)、image(图片base64)或按照img2text原生messages结构。")
    public String img2TextForChat(String params) {
        String safe = params == null ? "{}" : params;
        log.info("[ConversationOcrToolkit] img2TextForChat 调用");
        return zhengyanPlatformTool.img2Text(safe);
    }

    @Tool(description = "大智部通用OCR（适合证件、票据、扫描件等结构化文字识别），" +
            "params为原始请求JSON字符串（与大智部generalRecognition接口约定一致），" +
            "内部会自动补充businessNo和appCode。")
    public String dazhiOcrGeneralForChat(String params) {
        String safe = params == null ? "{}" : params;
        log.info("[ConversationOcrToolkit] dazhiOcrGeneralForChat 调用");
        EnterpriseTool tool = toolRegistry.getTool(DAZHI_OCR_TOOL_NAME);
        if (tool == null) {
            // 这里仅返回最基础的 success/message 结构，便于上层直接 readTree
            return ToolResponse.toJson(objectMapper,
                    ToolResponse.failure("未注册大智部OCR工具（" + DAZHI_OCR_TOOL_NAME + "）"));
        }
        return tool.execute(safe).toJsonString();
    }
}

