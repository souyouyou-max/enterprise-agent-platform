package com.sinosig.aip.tools.impl;

import com.sinosig.aip.common.core.response.ToolResponse;
import com.sinosig.aip.tools.EnterpriseTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * 大智部通用 OCR 识别接口封装，对应原 imagere 项目中的
 * com.sinosig.imagere.common.outerClient.ocr.OcrClient#generalRecognition。
 *
 * 使用 Spring {@link org.springframework.web.client.RestTemplate} 发送 JSON 请求体（与 CallOcrDistinguishReqDTO 字段一致）。
 */
@Slf4j
@Component
public class DazhiOcrTool implements EnterpriseTool {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${aip.tools.dazhi.ocr.enabled:false}")
    private boolean enabled;

    @Value("${aip.tools.dazhi.ocr.general-url:}")
    private String generalOcrUrl;

    @Value("${aip.tools.dazhi.ocr.app-code:G209-GHQ-CLM-JIHEFENGXIANJIQIREN}")
    private String appCode;

    @Value("${aip.tools.dazhi.ocr.timeout-ms:20000}")
    private int timeoutMs;

    public DazhiOcrTool(ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    @Override
    public String getToolName() {
        return "callDazhiOcrGeneral";
    }

    @Override
    public String getDescription() {
        return "调用大智部通用OCR（generalRecognition）。必填：picContent（或 imageBase64）传入图片 base64；" +
                "appCode/businessNo 由工具内部自动填充。可选：picName、customData（仅在显式传入时附加到请求）。";
    }

    @Override
    public ToolResponse execute(String params) {
        if (!enabled) {
            return ToolResponse.failure("大智部OCR未启用（aip.tools.dazhi.ocr.enabled=false）");
        }
        if (generalOcrUrl == null || generalOcrUrl.isBlank()) {
            return ToolResponse.failure("未配置大智部通用OCR地址（aip.tools.dazhi.ocr.general-url）");
        }
        try {
            ObjectNode body = buildRequestBody(params);
            log.info("[DazhiOcrTool] 调用开始 url={}, timeoutMs={}", generalOcrUrl, timeoutMs);
            log.debug("[DazhiOcrTool] 请求体(picContent已省略)={}", summarizeBodyForLog(body));
            long start = System.currentTimeMillis();
            ResponseEntity<String> response = postJson(generalOcrUrl, body);
            long cost = System.currentTimeMillis() - start;
            log.info("[DazhiOcrTool] 调用完成 status={}, costMs={}", response.getStatusCode().value(), cost);
            log.debug("[DazhiOcrTool] 响应体={}", abbreviate(response.getBody(), 800));
            return ToolResponse.fromRawJson(buildUnifiedResponse(body, response));
        } catch (Exception e) {
            log.error("[DazhiOcrTool] 调用失败: {}", e.getMessage(), e);
            return ToolResponse.failure("调用大智部通用OCR失败: " + sanitize(e.getMessage()));
        }
    }

    private ObjectNode buildRequestBody(String params) throws Exception {
        JsonNode node;
        if (params == null || params.isBlank()) {
            node = objectMapper.createObjectNode();
        } else {
            node = objectMapper.readTree(params);
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("参数必须是 JSON 对象");
        }
        String picContent = extractPicContentBase64(node);
        String businessNo = UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        // 字段顺序与大智部接口保持一致：appCode → businessNo → picContent
        ObjectNode body = objectMapper.createObjectNode();
        body.put("appCode", appCode);
        body.put("businessNo", businessNo);
        body.put("picContent", picContent);

        // picName / customData 仅在调用方显式传入时才附加，避免多余字段干扰接口路由
        String picName = node.path("picName").asText(null);
        if (picName != null && !picName.isBlank()) {
            body.put("picName", picName);
        }
        String customData = node.path("customData").asText(null);
        if (customData != null && !customData.isBlank()) {
            body.put("customData", customData);
        }
        return body;
    }

    /**
     * 从入参中取纯 base64：优先 picContent，其次 imageBase64；去掉 data:...;base64, 前缀。
     */
    private String extractPicContentBase64(JsonNode node) {
        String raw = node.path("picContent").asText("");
        if (raw.isBlank()) {
            raw = node.path("imageBase64").asText("");
        }
        if (raw.startsWith("data:")) {
            int comma = raw.indexOf(',');
            if (comma >= 0 && comma + 1 < raw.length()) {
                raw = raw.substring(comma + 1);
            }
        }
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("picContent 或 imageBase64 不能为空");
        }
        return raw;
    }

    private String summarizeBodyForLog(ObjectNode body) throws Exception {
        ObjectNode copy = objectMapper.createObjectNode();
        copy.put("businessNo", body.path("businessNo").asText(""));
        copy.put("appCode", body.path("appCode").asText(""));
        copy.put("picName", body.path("picName").asText(""));
        copy.put("customData", body.path("customData").asText(""));
        String pc = body.path("picContent").asText("");
        copy.put("picContent", pc.length() > 80 ? pc.substring(0, 80) + "...(len=" + pc.length() + ")" : pc);
        return objectMapper.writeValueAsString(copy);
    }

    private ResponseEntity<String> postJson(String url, ObjectNode body) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        return restTemplate.postForEntity(url, entity, String.class);
    }

    private String buildUnifiedResponse(ObjectNode requestBody, ResponseEntity<String> response) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        int statusCode = response.getStatusCode().value();
        root.put("success", statusCode >= 200 && statusCode < 300);
        root.put("httpStatus", statusCode);
        // 仅记录请求的元信息，不回传 picContent（base64 可达几百 KB，放入响应会撑爆 LLM 上下文）
        ObjectNode reqMeta = objectMapper.createObjectNode();
        reqMeta.put("appCode", requestBody.path("appCode").asText(""));
        reqMeta.put("businessNo", requestBody.path("businessNo").asText(""));
        reqMeta.put("picContentLen", requestBody.path("picContent").asText("").length());
        root.set("request", reqMeta);
        JsonNode respNode;
        try {
            respNode = objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            respNode = objectMapper.createObjectNode().put("raw", response.getBody());
        }
        root.set("response", respNode);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
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

    private String sanitize(String msg) {
        return msg == null ? "unknown" : msg.replace("\"", "'");
    }
}
