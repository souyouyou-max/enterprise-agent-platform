package com.sinosig.aip.common.core.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

/**
 * 企业工具（EnterpriseTool）统一返回结构中最常见的错误形态：
 * {"success":false,"message":"..."}
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolResponse {

    private boolean success;
    private String message;

    /**
     * 成功场景通常需要保留工具原始 JSON 结构（包含 action/httpStatus/request/response 等字段）。
     * 调用链只要最终要把结果当作 JSON 字符串即可，避免丢字段。
     */
    @JsonIgnore
    private String rawJson;

    private ToolResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static ToolResponse failure(String message) {
        return new ToolResponse(false, message);
    }

    public static ToolResponse fromRawJson(String rawJson) {
        ToolResponse resp = new ToolResponse(true, null);
        resp.rawJson = rawJson;
        return resp;
    }

    /**
     * 工具层统一输出 JSON 字符串。
     * - 若原始 JSON 存在：直接返回，保持字段完整
     * - 否则：构造最小 {success,message} 结构
     */
    public String toJsonString() {
        if (rawJson != null && !rawJson.isBlank()) {
            return rawJson;
        }
        return "{\"success\":" + success + ",\"message\":\"" + escapeJson(message) + "\"}";
    }

    // 兼容旧代码：仍允许把 ToolResponse 序列化成 JSON 字符串
    public static String toJson(ObjectMapper objectMapper, ToolResponse response) {
        if (response == null) {
            return "{\"success\":false,\"message\":\"unknown\"}";
        }
        try {
            return response.toJsonString();
        } catch (Exception e) {
            String msg = messageSafe(response.message);
            return "{\"success\":false,\"message\":\"" + msg + "\"}";
        }
    }

    private static String messageSafe(String msg) {
        if (msg == null) {
            return "unknown";
        }
        return msg.replace("\"", "'");
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "unknown";
        }
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}

