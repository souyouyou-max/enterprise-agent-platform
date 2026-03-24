package com.sinosig.aip.tools.impl;

import com.sinosig.aip.tools.EnterpriseTool;
import com.sinosig.aip.common.core.response.ToolResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 正言平台统一工具：
 * 1) img2text: /ai/ability/multimodal/v1/img2text
 */
@Slf4j
@Component
public class ZhengyanPlatformTool implements EnterpriseTool {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    @Value("${aip.tools.zhengyan.platform.enabled:false}")
    private boolean enabled;

    @Value("${aip.tools.zhengyan.platform.timeout-ms:20000}")
    private int timeoutMs;

    @Value("${aip.tools.zhengyan.platform.authorization}")
    private String authorization;

    @Value("${aip.tools.zhengyan.platform.app-id}")
    private String appId;

    @Value("${aip.tools.zhengyan.platform.endpoints.img2text}")
    private String img2TextEndpoint;

    @Value("${aip.tools.zhengyan.platform.default-model-type}")
    private String defaultModelType;

    public ZhengyanPlatformTool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getToolName() {
        return "callZhengyanPlatform";
    }

    @Override
    public String getDescription() {
        return "统一调用正言平台能力。action可选img2text，body为对应接口请求体。";
    }

    @Override
    public ToolResponse execute(String params) {
        try {
            JsonNode root = params == null || params.isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(params);
            String action = root.path("action").asText("");
            JsonNode body = root.path("body");
            String bodyJson = body.isMissingNode() ? "{}" : objectMapper.writeValueAsString(body);

            return switch (action) {
                case "img2text" -> ToolResponse.fromRawJson(img2Text(bodyJson));
                default -> ToolResponse.failure("action 必须是 img2text");
            };
        } catch (Exception e) {
            return ToolResponse.failure("参数格式错误: " + sanitize(e.getMessage()));
        }
    }

    public String img2Text(String params) {
        if (!preCheck()) {
            String errJson = checkError();
            // preCheck 失败时必须打印 warn，否则调用方完全看不到任何日志，
            // 表现为"分片立即失败但无 API 调用记录"。
            log.warn("[ZhengyanPlatformTool] img2text 跳过调用（配置不满足）: {}", abbreviate(errJson, 300));
            return errJson;
        }
        try {
            ObjectNode body = buildImg2TextBody(params);
            String modelType = extractModelType(params, true);
            log.info("[ZhengyanPlatformTool] 调用开始 action=img2text,appId={}, endpoint={}, modelType={}, timeoutMs={}, authConfigured={}, appIdConfigured={}", appId,
                    img2TextEndpoint, safeValue(modelType), timeoutMs, hasAuth(), hasAppId());
            log.debug("[ZhengyanPlatformTool] 请求摘要 action=img2text, body={}", summarizeRequest("img2text", body));
            long start = System.currentTimeMillis();
            HttpResponse<String> response = postJson(img2TextEndpoint, body, modelType);
            long cost = System.currentTimeMillis() - start;
            log.info("[ZhengyanPlatformTool] 调用完成 action=img2text, status={}, costMs={}", response.statusCode(), cost);
            log.debug("[ZhengyanPlatformTool] 响应摘要 action=img2text, body={}", abbreviate(response.body(), 600));
            return buildUnifiedResponse("img2text", response.statusCode(), body, response.body(), "text");
        } catch (Exception e) {
            log.error("[ZhengyanPlatformTool] img2text 调用失败: {}", e.getMessage(), e);
            return ToolResponse.toJson(objectMapper,
                    ToolResponse.failure("调用img2text失败: " + sanitize(e.getMessage())));
        }
    }

    private HttpResponse<String> postJson(String endpoint, ObjectNode body, String modelType) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .header("Authorization", authorization)
                .header("App-Id", appId)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        if (modelType != null && !modelType.isBlank()) {
            req.header("Model-Type", modelType);
        }
        return httpClient.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private ObjectNode buildImg2TextBody(String params) throws Exception {
        JsonNode node = parseToNode(params);
        if (!node.has("user_info") || !node.get("user_info").isObject()) {
            throw new IllegalArgumentException("user_info 不能为空");
        }

        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode messages = objectMapper.createArrayNode();
        if (node.has("messages") && node.get("messages").isArray() && node.get("messages").size() > 0) {
            messages = (ArrayNode) node.get("messages");
        } else {
            String text = node.path("text").asText("");
            String image = node.path("image").asText("");
            if (text.isBlank()) {
                throw new IllegalArgumentException("text 不能为空");
            }
            if (image.isBlank()) {
                throw new IllegalArgumentException("image 不能为空（需base64编码）");
            }
            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            ArrayNode content = objectMapper.createArrayNode();

            ObjectNode imageNode = objectMapper.createObjectNode();
            imageNode.put("type", "image_url");
            ObjectNode imageUrl = objectMapper.createObjectNode();
            imageUrl.put("url", toDataUrl(image));
            imageNode.set("image_url", imageUrl);
            content.add(imageNode);

            ObjectNode textNode = objectMapper.createObjectNode();
            textNode.put("type", "text");
            textNode.put("text", text);
            content.add(textNode);
            userMsg.set("content", content);
            messages.add(userMsg);
        }
        root.set("messages", messages);
        root.set("user_info", node.get("user_info"));
        if (node.has("history") && node.get("history").isArray()) {
            root.set("history", node.get("history"));
        }
        if (node.has("ext_model") && node.get("ext_model").isObject()) {
            root.set("ext_model", node.get("ext_model"));
        }
        if (node.has("ext_fields") && node.get("ext_fields").isObject()) {
            root.set("ext_fields", node.get("ext_fields"));
        }
        return root;
    }

    private JsonNode parseToNode(String params) throws Exception {
        return params == null || params.isBlank()
                ? objectMapper.createObjectNode()
                : objectMapper.readTree(params);
    }

    private String buildUnifiedResponse(String action, int status, ObjectNode requestBody, String rawBody, String answerField) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("action", action);
        root.put("success", status >= 200 && status < 300);
        root.put("httpStatus", status);
        root.set("request", requestBody);

        JsonNode upstream;
        try {
            upstream = objectMapper.readTree(rawBody);
        } catch (Exception ignore) {
            upstream = objectMapper.createObjectNode().put("raw", rawBody == null ? "" : rawBody);
        }
        root.set("response", upstream);
        JsonNode textNode = upstream.path("data").path(answerField);
        if (textNode.isTextual()) {
            root.put("result", textNode.asText());
        }
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    private String extractModelType(String params, boolean allowDefault) {
        try {
            JsonNode node = parseToNode(params);
            if (node.has("model_type") && node.get("model_type").isTextual()) {
                return node.get("model_type").asText();
            }
        } catch (Exception ignore) {
            // ignore
        }
        return allowDefault ? defaultModelType : null;
    }

    private boolean preCheck() {
        return enabled
                && authorization != null && !authorization.isBlank()
                && appId != null && !appId.isBlank();
    }

    private String checkError() {
        if (!enabled) {
            return ToolResponse.toJson(objectMapper,
                    ToolResponse.failure("正言平台工具未启用（aip.tools.zhengyan.platform.enabled=false）"));
        }
        if (authorization == null || authorization.isBlank()) {
            return ToolResponse.toJson(objectMapper,
                    ToolResponse.failure("未配置Authorization（aip.tools.zhengyan.platform.authorization）"));
        }
        if (appId == null || appId.isBlank()) {
            return ToolResponse.toJson(objectMapper,
                    ToolResponse.failure("未配置App-Id（aip.tools.zhengyan.platform.app-id）"));
        }
        return ToolResponse.toJson(objectMapper, ToolResponse.failure("配置不完整"));
    }

    private String sanitize(String msg) {
        return msg == null ? "unknown" : msg.replace("\"", "'");
    }

    private boolean hasAuth() {
        return authorization != null && !authorization.isBlank();
    }

    private boolean hasAppId() {
        return appId != null && !appId.isBlank();
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "(empty)" : value;
    }

    private String summarizeRequest(String action, ObjectNode body) {
        try {
            ObjectNode summary = objectMapper.createObjectNode();
            if ("img2text".equals(action)) {
                summary.put("messagesCount", body.path("messages").isArray() ? body.path("messages").size() : 0);
                summary.put("imageParts", countContentParts(body.path("messages"), "image_url"));
                summary.put("textParts", countContentParts(body.path("messages"), "text"));
                summary.set("bodyMasked", maskImg2TextBody(body));
                summary.set("user_info", body.path("user_info"));
                summary.put("hasHistory", body.has("history"));
                summary.put("hasExtModel", body.has("ext_model"));
                summary.put("hasExtFields", body.has("ext_fields"));
            } else {
                summary.put("session_id", body.path("session_id").asText(""));
                summary.put("bot_code", body.path("bot_code").asText(""));
                summary.put("input", abbreviate(body.path("input").asText(""), 160));
                summary.set("user_info", body.path("user_info"));
                summary.put("stream", body.path("stream").asText(""));
                summary.put("is_return_docurl", body.path("is_return_docurl").asText(""));
                summary.put("hasExtModel", body.has("ext_model"));
                summary.put("hasExtFields", body.has("ext_fields"));
            }
            return objectMapper.writeValueAsString(summary);
        } catch (Exception e) {
            return "{\"summaryError\":\"" + sanitize(e.getMessage()) + "\"}";
        }
    }

    private int countContentParts(JsonNode messages, String typeName) {
        int count = 0;
        if (!messages.isArray()) {
            return count;
        }
        for (JsonNode msg : messages) {
            JsonNode content = msg.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode part : content) {
                if (typeName.equals(part.path("type").asText(""))) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 以“原始请求结构”输出日志，仅对 base64 内容脱敏，便于和 curl 示例逐字段对照。
     */
    private ObjectNode maskImg2TextBody(ObjectNode body) {
        ObjectNode masked = body.deepCopy();
        JsonNode messages = masked.path("messages");
        if (!messages.isArray()) {
            return masked;
        }
        for (JsonNode msg : messages) {
            JsonNode content = msg.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode part : content) {
                if (!"image_url".equals(part.path("type").asText(""))) {
                    continue;
                }
                JsonNode imageUrlNode = part.path("image_url");
                if (!imageUrlNode.isObject()) {
                    continue;
                }
                ObjectNode imageUrlObj = (ObjectNode) imageUrlNode;
                String url = imageUrlObj.path("url").asText("");
                String maskedUrl = "<omitted-base64>";
                if (url.startsWith("data:")) {
                    int commaIndex = url.indexOf(',');
                    String prefix = commaIndex > 0 ? url.substring(0, commaIndex + 1) : "data:,";
                    maskedUrl = prefix + "<omitted-base64>";
                } else if (!url.isBlank()) {
                    maskedUrl = abbreviate(url, 120);
                }
                imageUrlObj.put("url", maskedUrl);
            }
        }
        return masked;
    }

    private String abbreviate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen) + "...(truncated)";
    }

    private String toDataUrl(String image) {
        if (image == null || image.isBlank()) {
            return "";
        }
        if (image.startsWith("data:")) {
            return image;
        }
        return "data:image/jpeg;base64," + image;
    }
}

