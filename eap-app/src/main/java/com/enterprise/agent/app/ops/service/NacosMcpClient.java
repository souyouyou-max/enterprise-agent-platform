package com.enterprise.agent.app.ops.service;

import com.enterprise.agent.app.ops.config.OpsAgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 轻量 Nacos MCP 客户端（一期实现）。
 *
 * <p>约定 MCP 网关提供：
 * POST /nacos/get-config
 * POST /nacos/config-history
 * POST /nacos/publish-config
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NacosMcpClient {

    private final OpsAgentProperties opsAgentProperties;
    private final ObjectMapper objectMapper;

    public McpCallResult getConfig(String namespace, String group, String dataId) {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("namespace", namespace == null ? "" : namespace);
        req.put("group", group == null ? "DEFAULT_GROUP" : group);
        req.put("dataId", dataId == null ? "" : dataId);
        return postJson("/nacos/get-config", req);
    }

    public McpCallResult getConfigHistory(String namespace, String group, String dataId) {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("namespace", namespace == null ? "" : namespace);
        req.put("group", group == null ? "DEFAULT_GROUP" : group);
        req.put("dataId", dataId == null ? "" : dataId);
        return postJson("/nacos/config-history", req);
    }

    public McpCallResult publishConfig(String namespace,
                                       String group,
                                       String dataId,
                                       JsonNode proposedChanges,
                                       String comment) {
        ObjectNode req = objectMapper.createObjectNode();
        req.put("namespace", namespace == null ? "" : namespace);
        req.put("group", group == null ? "DEFAULT_GROUP" : group);
        req.put("dataId", dataId == null ? "" : dataId);
        req.set("proposedChanges", proposedChanges == null ? objectMapper.createObjectNode() : proposedChanges);
        req.put("comment", comment == null ? "" : comment);
        return postJson("/nacos/publish-config", req);
    }

    public boolean enabled() {
        OpsAgentProperties.NacosMcp c = opsAgentProperties.getNacosMcp();
        return c.isEnabled() && c.getBaseUrl() != null && !c.getBaseUrl().isBlank();
    }

    private McpCallResult postJson(String path, JsonNode body) {
        if (!enabled()) {
            return new McpCallResult(false, null, "Nacos MCP 未启用或缺少 baseUrl");
        }
        OpsAgentProperties.NacosMcp cfg = opsAgentProperties.getNacosMcp();
        HttpURLConnection conn = null;
        try {
            String base = cfg.getBaseUrl().endsWith("/") ? cfg.getBaseUrl().substring(0, cfg.getBaseUrl().length() - 1) : cfg.getBaseUrl();
            URL url = new URL(base + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(cfg.getTimeoutMs());
            conn.setReadTimeout(cfg.getTimeoutMs());
            conn.setRequestProperty("Content-Type", "application/json");
            if (cfg.getToken() != null && !cfg.getToken().isBlank()) {
                conn.setRequestProperty("Authorization", "Bearer " + cfg.getToken());
            }

            byte[] bytes = objectMapper.writeValueAsBytes(body);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }

            int code = conn.getResponseCode();
            InputStream is = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) {
                return new McpCallResult(false, null, "MCP 返回空响应，httpStatus=" + code);
            }
            String resp = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            JsonNode json = parseJsonSafe(resp);
            if (code >= 200 && code < 300) {
                return new McpCallResult(true, json, null);
            }
            return new McpCallResult(false, json, "MCP 调用失败，httpStatus=" + code);
        } catch (Exception e) {
            log.warn("[Ops][NacosMcp] 调用失败 path={} err={}", path, e.toString());
            return new McpCallResult(false, null, "MCP 调用异常: " + e.getMessage());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private JsonNode parseJsonSafe(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("raw", text);
            return node;
        }
    }

    public record McpCallResult(boolean success, JsonNode payload, String message) {}
}
