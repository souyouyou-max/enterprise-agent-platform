package com.enterprise.agent.tools.impl;

import com.enterprise.agent.tools.EnterpriseTool;
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
 * 2) professional qa: /ai/ability/professional/v1/qa
 */
@Slf4j
@Component
public class ZhengyanPlatformTool implements EnterpriseTool {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    @Value("${eap.tools.zhengyan.platform.enabled:false}")
    private boolean enabled;

    @Value("${eap.tools.zhengyan.platform.timeout-ms:20000}")
    private int timeoutMs;

    @Value("${eap.tools.zhengyan.platform.authorization:}")
    private String authorization;

    @Value("${eap.tools.zhengyan.platform.app-id:}")
    private String appId;

    @Value("${eap.tools.zhengyan.platform.endpoints.img2text:https://zhengyan.sinosig.com/ai/ability/multimodal/v1/img2text}")
    private String img2TextEndpoint;

    @Value("${eap.tools.zhengyan.platform.endpoints.professional-qa:https://zhengyan.sinosig.com/ai/ability/professional/v1/qa}")
    private String professionalQaEndpoint;

    @Value("${eap.tools.zhengyan.platform.default-model-type:}")
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
        return "统一调用正言平台能力。action可选img2text/professionalQa，body为对应接口请求体。";
    }

    @Override
    public String execute(String params) {
        try {
            JsonNode root = params == null || params.isBlank()
                    ? objectMapper.createObjectNode()
                    : objectMapper.readTree(params);
            String action = root.path("action").asText("");
            JsonNode body = root.path("body");
            String bodyJson = body.isMissingNode() ? "{}" : objectMapper.writeValueAsString(body);

            return switch (action) {
                case "img2text" -> img2Text(bodyJson);
                case "professionalQa", "professional-qa" -> professionalQa(bodyJson);
                default -> "{\"success\":false,\"message\":\"action 必须是 img2text 或 professionalQa\"}";
            };
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"参数格式错误: " + sanitize(e.getMessage()) + "\"}";
        }
    }

    public String img2Text(String params) {
        if (!preCheck()) {
            return checkError();
        }
        try {
            ObjectNode body = buildImg2TextBody(params);
            String modelType = extractModelType(params, true);
            log.info("[ZhengyanPlatformTool] 调用开始 action=img2text, endpoint={}, modelType={}, timeoutMs={}, authConfigured={}, appIdConfigured={}",
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
            return "{\"success\":false,\"message\":\"调用img2text失败: " + sanitize(e.getMessage()) + "\"}";
        }
    }

    public String professionalQa(String params) {
        if (!preCheck()) {
            return checkError();
        }
        try {
            ObjectNode body = buildProfessionalQaBody(params);
            log.info("[ZhengyanPlatformTool] 调用开始 action=professionalQa, endpoint={}, timeoutMs={}, authConfigured={}, appIdConfigured={}",
                    professionalQaEndpoint, timeoutMs, hasAuth(), hasAppId());
            log.debug("[ZhengyanPlatformTool] 请求摘要 action=professionalQa, body={}", summarizeRequest("professionalQa", body));
            long start = System.currentTimeMillis();
            HttpResponse<String> response = postJson(professionalQaEndpoint, body, null);
            long cost = System.currentTimeMillis() - start;
            log.info("[ZhengyanPlatformTool] 调用完成 action=professionalQa, status={}, costMs={}", response.statusCode(), cost);
            log.debug("[ZhengyanPlatformTool] 响应摘要 action=professionalQa, body={}", abbreviate(response.body(), 600));
            return buildUnifiedResponse("professionalQa", response.statusCode(), body, response.body(), "answer");
        } catch (Exception e) {
            log.error("[ZhengyanPlatformTool] professionalQa 调用失败: {}", e.getMessage(), e);
            return "{\"success\":false,\"message\":\"调用professionalQa失败: " + sanitize(e.getMessage()) + "\"}";
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

    private ObjectNode buildProfessionalQaBody(String params) throws Exception {
        JsonNode node = parseToNode(params);
        String sessionId = node.path("session_id").asText("");
        String input = node.path("input").asText("");
        String botCode = node.path("bot_code").asText("");
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("session_id 不能为空");
        }
        if (input.isBlank()) {
            throw new IllegalArgumentException("input 不能为空");
        }
        if (botCode.isBlank()) {
            throw new IllegalArgumentException("bot_code 不能为空");
        }
        if (!node.has("user_info") || !node.get("user_info").isObject()) {
            throw new IllegalArgumentException("user_info 不能为空");
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.put("session_id", sessionId);
        root.put("input", input);
        root.put("bot_code", botCode);
        root.set("user_info", node.get("user_info"));
        if (node.has("ext_model") && node.get("ext_model").isObject()) {
            root.set("ext_model", node.get("ext_model"));
        }
        if (node.has("ext_fields") && node.get("ext_fields").isObject()) {
            root.set("ext_fields", node.get("ext_fields"));
        }
        if (node.has("stream") && node.get("stream").isBoolean()) {
            root.put("stream", node.get("stream").asBoolean());
        }
        if (node.has("is_return_docurl") && node.get("is_return_docurl").canConvertToInt()) {
            root.put("is_return_docurl", node.get("is_return_docurl").asInt());
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
            return "{\"success\":false,\"message\":\"正言平台工具未启用（eap.tools.zhengyan.platform.enabled=false）\"}";
        }
        if (authorization == null || authorization.isBlank()) {
            return "{\"success\":false,\"message\":\"未配置Authorization（eap.tools.zhengyan.platform.authorization）\"}";
        }
        if (appId == null || appId.isBlank()) {
            return "{\"success\":false,\"message\":\"未配置App-Id（eap.tools.zhengyan.platform.app-id）\"}";
        }
        return "{\"success\":false,\"message\":\"配置不完整\"}";
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
                if (body.path("messages").isArray() && body.path("messages").size() > 0) {
                    JsonNode firstMsg = body.path("messages").get(0);
                    summary.put("firstRole", firstMsg.path("role").asText(""));
                    JsonNode content = firstMsg.path("content");
                    if (content.isArray()) {
                        summary.put("firstContentCount", content.size());
                        int imageParts = 0;
                        int textParts = 0;
                        int firstImageUrlLength = 0;
                        String firstText = "";
                        for (JsonNode part : content) {
                            String type = part.path("type").asText("");
                            if ("image_url".equals(type)) {
                                imageParts++;
                                if (firstImageUrlLength == 0) {
                                    firstImageUrlLength = part.path("image_url").path("url").asText("").length();
                                }
                            } else if ("text".equals(type)) {
                                textParts++;
                                if (firstText.isBlank()) {
                                    firstText = part.path("text").asText("");
                                }
                            }
                        }
                        summary.put("imageParts", imageParts);
                        summary.put("textParts", textParts);
                        summary.put("firstImageUrlLength", firstImageUrlLength);
                        summary.put("firstText", abbreviate(firstText, 200));
                    }
                }
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

