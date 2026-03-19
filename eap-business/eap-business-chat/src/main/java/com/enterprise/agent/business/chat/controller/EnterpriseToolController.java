package com.enterprise.agent.business.chat.controller;

import com.enterprise.agent.business.chat.service.DocumentImageConverter;
import com.enterprise.agent.common.core.response.ResponseResult;
import com.enterprise.agent.tools.ToolRegistry;
import com.enterprise.agent.tools.impl.CrmTool;
import com.enterprise.agent.tools.impl.EmployeeTool;
import com.enterprise.agent.tools.impl.SalesDataTool;
import com.enterprise.agent.tools.impl.ZhengyanPlatformTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 企业工具 REST API - 直接调用企业工具（销售数据/员工查询等）
 */
@Tag(name = "企业工具 API", description = "直接查询销售数据、员工信息、CRM 数据等企业工具")
@RestController
@RequestMapping("/api/v1/enterprise")
@RequiredArgsConstructor
public class EnterpriseToolController {

    private final SalesDataTool salesDataTool;
    private final EmployeeTool employeeTool;
    private final CrmTool crmTool;
    private final ZhengyanPlatformTool zhengyanPlatformTool;
    private final DocumentImageConverter documentImageConverter;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    @Operation(summary = "查询销售数据", description = "按部门查询季度销售数据（dept 参数可为 华南区/华北区/华东区/西部区/all）")
    @GetMapping("/sales/{dept}")
    public ResponseResult<Object> getSalesData(
            @PathVariable String dept,
            @RequestParam(defaultValue = "Q4-2024") String quarter) throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("department", dept);
        params.put("quarter", quarter);
        String result = salesDataTool.execute(objectMapper.writeValueAsString(params));
        return ResponseResult.success(objectMapper.readValue(result, Object.class));
    }

    @Operation(summary = "查询员工信息")
    @GetMapping("/employees/{id}")
    public ResponseResult<Object> getEmployee(@PathVariable String id) throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("id", id);
        String result = employeeTool.execute(objectMapper.writeValueAsString(params));
        return ResponseResult.success(objectMapper.readValue(result, Object.class));
    }

    @Operation(summary = "查询 CRM 客户数据")
    @GetMapping("/crm/{customerId}")
    public ResponseResult<Object> getCrmData(@PathVariable String customerId) throws Exception {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("customerId", customerId);
        String result = crmTool.execute(objectMapper.writeValueAsString(params));
        return ResponseResult.success(objectMapper.readValue(result, Object.class));
    }

    @Operation(summary = "查询已注册工具列表")
    @GetMapping("/tools")
    public ResponseResult<Map<String, String>> listTools() {
        return ResponseResult.success(toolRegistry.listTools());
    }

    @Operation(summary = "调用正言图片识别转文本(img2text)", description = "请求体包含 text、image(base64)、user_info，可选history/ext_model/ext_fields")
    @PostMapping("/semantics/img2text")
    public ResponseResult<Object> img2Text(@RequestBody Map<String, Object> body) throws Exception {
        ObjectNode request = objectMapper.convertValue(body, ObjectNode.class);
        normalizeImg2TextRequest(request);
        String params = objectMapper.writeValueAsString(request);
        String result = zhengyanPlatformTool.img2Text(params);
        JsonNode toolNode = objectMapper.readTree(result);
        ObjectNode view = objectMapper.createObjectNode();
        view.put("action", toolNode.path("action").asText("img2text"));
        view.put("success", toolNode.path("success").asBoolean(false));
        view.put("httpStatus", toolNode.path("httpStatus").asInt(0));
        view.put("content", extractImg2TextContent(toolNode));
        view.set("response", toolNode.path("response"));
        return ResponseResult.success(objectMapper.convertValue(view, Object.class));
    }

    private void normalizeImg2TextRequest(ObjectNode request) throws Exception {
        JsonNode attachments = request.path("attachments");
        if (!attachments.isArray() || attachments.isEmpty()) {
            return;
        }
        ArrayNode content = objectMapper.createArrayNode();
        for (JsonNode attachment : attachments) {
            String name = attachment.path("name").asText("");
            String mimeType = attachment.path("mimeType").asText("");
            String base64 = attachment.path("base64").asText("");
            for (String dataUrl : documentImageConverter.toImageDataUrls(name, mimeType, base64)) {
                ObjectNode imageNode = objectMapper.createObjectNode();
                imageNode.put("type", "image_url");
                ObjectNode imageUrl = objectMapper.createObjectNode();
                imageUrl.put("url", dataUrl);
                imageNode.set("image_url", imageUrl);
                content.add(imageNode);
            }
        }
        String text = request.path("text").asText("");
        if (!text.isBlank()) {
            ObjectNode textNode = objectMapper.createObjectNode();
            textNode.put("type", "text");
            textNode.put("text", text);
            content.add(textNode);
        }
        if (!content.isEmpty()) {
            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.set("content", content);
            ArrayNode messages = objectMapper.createArrayNode();
            messages.add(userMessage);
            request.set("messages", messages);
        }
        request.remove("attachments");
    }

    @Operation(summary = "调用正言专业问答(professional qa)", description = "请求体包含 session_id/input/bot_code/user_info，可选ext_model/stream/ext_fields/is_return_docurl")
    @PostMapping("/semantics/professional-qa")
    public ResponseResult<Object> professionalQa(@RequestBody Map<String, Object> body) throws Exception {
        String params = objectMapper.writeValueAsString(body);
        String result = zhengyanPlatformTool.professionalQa(params);
        return ResponseResult.success(objectMapper.readValue(result, Object.class));
    }

    private String extractImg2TextContent(JsonNode toolNode) {
        String result = toolNode.path("result").asText("");
        if (!result.isBlank()) {
            return result;
        }
        JsonNode response = toolNode.path("response");
        JsonNode choices = response.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            String content = choices.get(0).path("message").path("content").asText("");
            if (!content.isBlank()) {
                return content;
            }
        }
        String text = response.path("data").path("text").asText("");
        if (!text.isBlank()) {
            return text;
        }
        return "";
    }
}
