package com.enterprise.agent.tools.impl;

import com.enterprise.agent.common.core.response.ToolResponse;
import com.enterprise.agent.tools.EnterpriseTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;

/**
 * Zhengyan 文本分类工具。
 * <p>
 * 说明：
 * 1) 默认将入参转为通用文本分类请求体：text/labels/topK/threshold
 * 2) 若对接文档字段与默认构造不同，可通过 requestBody 透传原始请求体
 */
@Slf4j
@Component
public class ZhengyanTextClassificationTool implements EnterpriseTool {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    @Value("${eap.tools.zhengyan.text-classification.enabled:false}")
    private boolean enabled;

    @Value("${eap.tools.zhengyan.text-classification.endpoint:}")
    private String endpoint;

    @Value("${eap.tools.zhengyan.text-classification.timeout-ms:15000}")
    private int timeoutMs;

    @Value("${eap.tools.zhengyan.text-classification.auth-header:Authorization}")
    private String authHeader;

    @Value("${eap.tools.zhengyan.text-classification.auth-prefix:Bearer }")
    private String authPrefix;

    @Value("${eap.tools.zhengyan.text-classification.api-key:}")
    private String apiKey;

    @Value("${eap.tools.zhengyan.text-classification.app-id:}")
    private String appId;

    @Value("${eap.tools.zhengyan.text-classification.app-id-header:X-App-Id}")
    private String appIdHeader;

    public ZhengyanTextClassificationTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getToolName() {
        return "classifyTextSemantics";
    }

    @Override
    public String getDescription() {
        return "调用正言语义文本分类接口，返回分类标签和置信度（支持requestBody透传）。";
    }

    @Override
    public ToolResponse execute(String params) {
        if (!enabled) {
            return ToolResponse.failure("正言文本分类工具未启用（eap.tools.zhengyan.text-classification.enabled=false）");
        }
        if (endpoint == null || endpoint.isBlank()) {
            return ToolResponse.failure("未配置正言接口地址（eap.tools.zhengyan.text-classification.endpoint）");
        }

        try {
            ObjectNode requestBody = buildRequestBody(params);
            String body = objectMapper.writeValueAsString(requestBody);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));

            if (appId != null && !appId.isBlank()) {
                requestBuilder.header(appIdHeader, appId);
            }
            if (apiKey != null && !apiKey.isBlank()) {
                requestBuilder.header(authHeader, (authPrefix == null ? "" : authPrefix) + apiKey);
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            return ToolResponse.fromRawJson(buildResponse(response.statusCode(), requestBody, response.body()));
        } catch (Exception e) {
            log.error("[ZhengyanTextClassificationTool] 调用失败: {}", e.getMessage(), e);
            return ToolResponse.failure("调用正言文本分类失败: " + sanitizeError(e.getMessage()));
        }
    }

    private ObjectNode buildRequestBody(String params) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        if (params == null || params.isBlank() || "{}".equals(params.trim())) {
            return root;
        }

        JsonNode paramNode = objectMapper.readTree(params);
        if (paramNode.has("requestBody") && paramNode.get("requestBody").isObject()) {
            return (ObjectNode) paramNode.get("requestBody");
        }

        String text = pickText(paramNode, "text", "content", "message", "query");
        if (text != null && !text.isBlank()) {
            root.put("text", text);
        }

        if (paramNode.has("labels") && paramNode.get("labels").isArray()) {
            root.set("labels", paramNode.get("labels"));
        } else if (paramNode.has("categories") && paramNode.get("categories").isArray()) {
            root.set("labels", paramNode.get("categories"));
        }

        if (paramNode.has("topK") && paramNode.get("topK").canConvertToInt()) {
            root.put("topK", paramNode.get("topK").asInt());
        }
        if (paramNode.has("threshold") && paramNode.get("threshold").isNumber()) {
            root.put("threshold", paramNode.get("threshold").asDouble());
        }

        if (paramNode.has("extra") && paramNode.get("extra").isObject()) {
            merge(root, (ObjectNode) paramNode.get("extra"));
        }
        return root;
    }

    private void merge(ObjectNode target, ObjectNode extra) {
        Iterator<Map.Entry<String, JsonNode>> iterator = extra.properties().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            target.set(entry.getKey(), entry.getValue());
        }
    }

    private String pickText(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && node.get(key).isTextual()) {
                return node.get(key).asText();
            }
        }
        return null;
    }

    private String buildResponse(int status, ObjectNode requestBody, String rawBody) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("success", status >= 200 && status < 300);
        root.put("status", status);
        root.set("request", requestBody);

        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(rawBody);
        } catch (Exception ignore) {
            parsed = objectMapper.createObjectNode().put("raw", rawBody == null ? "" : rawBody);
        }
        root.set("response", parsed);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    private String sanitizeError(String message) {
        if (message == null) {
            return "unknown";
        }
        return message.replace("\"", "'");
    }
}

