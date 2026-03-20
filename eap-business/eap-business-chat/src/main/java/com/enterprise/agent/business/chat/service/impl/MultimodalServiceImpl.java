package com.enterprise.agent.business.chat.service.impl;

import com.enterprise.agent.business.chat.service.DocumentImageConverter;
import com.enterprise.agent.business.chat.service.MultimodalService;
import com.enterprise.agent.common.ai.service.LlmService;
import com.enterprise.agent.tools.EnterpriseTool;
import com.enterprise.agent.tools.ToolRegistry;
import com.enterprise.agent.tools.impl.DazhiOcrTool;
import com.enterprise.agent.tools.impl.ZhengyanPlatformTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link MultimodalService} 默认实现。
 * 封装图文理解（img2text）与大智部通用 OCR 的调用、批量分页、引擎路由、文本提取等业务逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MultimodalServiceImpl implements MultimodalService {

    private static final String AUTO_OCR_MODE_DIRECT = "direct";
    private static final String AUTO_OCR_MODE_AGENT = "agent";
    private static final String TOOL_ZHENGYAN_PLATFORM = "callZhengyanPlatform";
    private static final String TOOL_DAZHI_OCR = "callDazhiOcrGeneral";
    private static final String STRATEGY_BATCH = "batch";
    private static final String STRATEGY_SINGLE = "single";

    private final ZhengyanPlatformTool zhengyanPlatformTool;
    private final DazhiOcrTool dazhiOcrTool;
    private final DocumentImageConverter documentImageConverter;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final LlmService llmService;

    @Value("${eap.tools.zhengyan.platform.img2text.max-images-per-call:2}")
    private int maxImagesPerCall;

    @Value("${eap.business.chat.auto-ocr.invoke-mode:direct}")
    private String autoOcrInvokeMode;

    // -------------------------------------------------------------------------
    // 公共业务方法（接口实现）
    // -------------------------------------------------------------------------

    @Override
    public ObjectNode img2Text(ObjectNode request) throws Exception {
        JsonNode attachments = request.path("attachments");
        JsonNode toolNode;
        if (attachments.isArray() && !attachments.isEmpty()) {
            toolNode = callImg2TextInBatches(request);
        } else {
            normalizeImg2TextRequest(request);
            String result = zhengyanPlatformTool.img2Text(objectMapper.writeValueAsString(request));
            toolNode = objectMapper.readTree(result);
        }
        return buildImg2TextView(toolNode);
    }

    @Override
    public ObjectNode autoOcr(ObjectNode request) throws Exception {
        String text = request.path("text").asText("");
        JsonNode attachments = request.path("attachments");
        boolean hasAttachments = attachments.isArray() && !attachments.isEmpty();
        boolean useAgentMode = AUTO_OCR_MODE_AGENT.equalsIgnoreCase(autoOcrInvokeMode);
        OcrPlan plan = useAgentMode ? decideOcrPlan(text, hasAttachments) : buildFallbackPlan(text, hasAttachments);
        String engine = plan.engine();
        boolean useBatch = STRATEGY_BATCH.equals(plan.strategy()) && hasAttachments;
        log.info("[MultimodalService] autoOcr 请求开始 invokeMode={}, hasAttachments={}, textLen={}",
                useAgentMode ? AUTO_OCR_MODE_AGENT : AUTO_OCR_MODE_DIRECT, hasAttachments, text == null ? 0 : text.length());
        log.info("[MultimodalService] autoOcr 路由决策 engine={}, strategy={}, invokeMode={}",
                engine, plan.strategy(), useAgentMode ? AUTO_OCR_MODE_AGENT : AUTO_OCR_MODE_DIRECT);

        if ("img2text".equals(engine)) {
            JsonNode toolNode;
            if (useBatch) {
                toolNode = callImg2TextInBatches(request);
            } else {
                normalizeImg2TextRequest(request);
                String result = useAgentMode
                        ? callImg2TextByRegistry(request)
                        : zhengyanPlatformTool.img2Text(objectMapper.writeValueAsString(request));
                toolNode = objectMapper.readTree(result);
            }
            ObjectNode view = buildImg2TextView(toolNode);
            view.put("engine", "img2text");
            return view;
        } else {
            JsonNode resultNode = useBatch
                    ? callDazhiOcrWithAttachments(request)
                    : objectMapper.readTree(useAgentMode
                    ? callDazhiByRegistry(request)
                    : dazhiOcrTool.execute(objectMapper.writeValueAsString(request)).toJsonString());
            return buildDazhiOcrView(resultNode);
        }
    }

    private OcrPlan decideOcrPlan(String text, boolean hasAttachments) {
        try {
            String prompt = """
                    你是 OCR 编排规划器。请根据用户输入和是否有附件，返回 JSON：
                    {"engine":"img2text|dazhi-ocr","strategy":"batch|single"}

                    规则：
                    1) engine=img2text：适合总结/问答/理解/分析图片或文档。
                    2) engine=dazhi-ocr：适合纯文本识别、证件票据字段抽取。
                    3) strategy=batch 仅在有附件时可用；无附件时必须 single。
                    4) 只返回 JSON，不要解释。

                    hasAttachments=%s
                    userText=%s
                    """.formatted(hasAttachments ? "true" : "false", text == null ? "" : text.trim());
            String ans = llmService.simpleChat(prompt);
            OcrPlan parsed = parsePlan(ans, hasAttachments);
            if (parsed != null) {
                return parsed;
            }
        } catch (Exception e) {
            log.warn("[MultimodalService] OCR plan 决策失败，降级默认策略: {}", e.getMessage());
        }
        return buildFallbackPlan(text, hasAttachments);
    }

    private OcrPlan parsePlan(String ans, boolean hasAttachments) {
        if (ans == null || ans.isBlank()) {
            return null;
        }
        String lower = ans.toLowerCase();
        String engine = lower.contains("img2text") ? "img2text"
                : (lower.contains("dazhi-ocr") || lower.contains("dazhi") || lower.contains("ocr")) ? "dazhi-ocr" : null;
        if (engine == null) {
            return null;
        }
        String strategy = lower.contains("batch") ? STRATEGY_BATCH : STRATEGY_SINGLE;
        if (!hasAttachments) {
            strategy = STRATEGY_SINGLE;
        }
        return new OcrPlan(engine, strategy);
    }

    private OcrPlan buildFallbackPlan(String text, boolean hasAttachments) {
        String engine = decideOcrEngine(text, hasAttachments);
        String strategy = hasAttachments ? STRATEGY_BATCH : STRATEGY_SINGLE;
        return new OcrPlan(engine, strategy);
    }

    private String callImg2TextByRegistry(ObjectNode request) throws Exception {
        log.info("[MultimodalService] autoOcr(agent) 调用工具 toolName={}", TOOL_ZHENGYAN_PLATFORM);
        EnterpriseTool tool = toolRegistry.getTool(TOOL_ZHENGYAN_PLATFORM);
        if (tool == null) {
            throw new IllegalStateException("未注册工具: " + TOOL_ZHENGYAN_PLATFORM);
        }
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("action", "img2text");
        payload.set("body", request);
        return tool.execute(objectMapper.writeValueAsString(payload)).toJsonString();
    }

    private String callDazhiByRegistry(ObjectNode request) throws Exception {
        log.info("[MultimodalService] autoOcr(agent) 调用工具 toolName={}", TOOL_DAZHI_OCR);
        EnterpriseTool tool = toolRegistry.getTool(TOOL_DAZHI_OCR);
        if (tool == null) {
            throw new IllegalStateException("未注册工具: " + TOOL_DAZHI_OCR);
        }
        return tool.execute(objectMapper.writeValueAsString(request)).toJsonString();
    }

    @Override
    public JsonNode dazhiOcrGeneral(ObjectNode request) throws Exception {
        JsonNode attachments = request.path("attachments");
        if (attachments.isArray() && !attachments.isEmpty()) {
            return callDazhiOcrWithAttachments(request);
        }
        return objectMapper.readTree(dazhiOcrTool.execute(objectMapper.writeValueAsString(request)).toJsonString());
    }

    // -------------------------------------------------------------------------
    // 私有：img2text 分批处理
    // -------------------------------------------------------------------------

    private JsonNode callImg2TextInBatches(ObjectNode request) throws Exception {
        String text = request.path("text").asText("");
        String finalText = text.isBlank() ? "请提取附件内容并给出结构化总结" : text;
        List<String> imageDataUrls = extractAttachmentImageDataUrls(request.path("attachments"));

        if (imageDataUrls.isEmpty()) {
            normalizeImg2TextRequest(request);
            return objectMapper.readTree(
                    zhengyanPlatformTool.img2Text(objectMapper.writeValueAsString(request)));
        }

        int step = Math.max(1, maxImagesPerCall);
        List<String> contents = new ArrayList<>();
        ArrayNode mergedResponses = objectMapper.createArrayNode();
        boolean allSuccess = true;
        int maxHttpStatus = 200;

        for (int i = 0; i < imageDataUrls.size(); i += step) {
            int startIndex = i;
            int endExclusive = Math.min(i + step, imageDataUrls.size());
            List<String> batch = imageDataUrls.subList(startIndex, endExclusive);
            int totalPages = imageDataUrls.size();
            int startPageNo = startIndex + 1;
            int endPageNo = endExclusive;
            String batchText = """
                    当前处理文档第 %d-%d 页（共 %d 页）。
                    请只基于本批页内容进行识别与总结，保留关键字段与原文要点。

                    用户诉求：%s
                    """.formatted(startPageNo, endPageNo, totalPages, finalText);
            ObjectNode batchRequest = buildRequestByImageBatch(request, batch, batchText);
            JsonNode node = objectMapper.readTree(
                    zhengyanPlatformTool.img2Text(objectMapper.writeValueAsString(batchRequest)));
            mergedResponses.add(node.path("response"));
            String content = extractImg2TextContent(node);
            if (!content.isBlank()) {
                contents.add("[第" + startPageNo + "-" + endPageNo + "页]\n" + content);
            }
            allSuccess = allSuccess && node.path("success").asBoolean(false);
            maxHttpStatus = Math.max(maxHttpStatus, node.path("httpStatus").asInt(200));
        }

        ObjectNode merged = objectMapper.createObjectNode();
        merged.put("action", "img2text");
        merged.put("success", allSuccess);
        merged.put("httpStatus", maxHttpStatus);
        merged.put("result", String.join("\n\n", contents));
        merged.set("response", mergedResponses);
        return merged;
    }

    private ObjectNode buildRequestByImageBatch(ObjectNode origin, List<String> batch, String text) {
        ObjectNode req = origin.deepCopy();
        req.remove("attachments");
        ArrayNode content = objectMapper.createArrayNode();
        for (String dataUrl : batch) {
            ObjectNode imageNode = objectMapper.createObjectNode();
            imageNode.put("type", "image_url");
            ObjectNode imageUrl = objectMapper.createObjectNode();
            imageUrl.put("url", dataUrl);
            imageNode.set("image_url", imageUrl);
            content.add(imageNode);
        }
        ObjectNode textNode = objectMapper.createObjectNode();
        textNode.put("type", "text");
        textNode.put("text", text);
        content.add(textNode);
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.set("content", content);
        ArrayNode messages = objectMapper.createArrayNode();
        messages.add(userMessage);
        req.set("messages", messages);
        return req;
    }

    /** 将 attachments 字段转换为 messages[image_url] 格式，原地修改 request。 */
    private void normalizeImg2TextRequest(ObjectNode request) throws Exception {
        JsonNode attachments = request.path("attachments");
        if (!attachments.isArray() || attachments.isEmpty()) {
            return;
        }
        List<String> imageDataUrls = extractAttachmentImageDataUrls(attachments);
        if (imageDataUrls.isEmpty()) {
            return;
        }
        String text = request.path("text").asText("");
        ArrayNode content = objectMapper.createArrayNode();
        for (String dataUrl : imageDataUrls) {
            ObjectNode imageNode = objectMapper.createObjectNode();
            imageNode.put("type", "image_url");
            ObjectNode imageUrl = objectMapper.createObjectNode();
            imageUrl.put("url", dataUrl);
            imageNode.set("image_url", imageUrl);
            content.add(imageNode);
        }
        ObjectNode textNode = objectMapper.createObjectNode();
        textNode.put("type", "text");
        textNode.put("text", text.isBlank() ? "请提取附件内容并给出结构化总结" : text);
        content.add(textNode);
        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.set("content", content);
        ArrayNode messages = objectMapper.createArrayNode();
        messages.add(userMessage);
        request.set("messages", messages);
        request.remove("attachments");
    }

    private List<String> extractAttachmentImageDataUrls(JsonNode attachments) throws Exception {
        List<String> result = new ArrayList<>();
        for (JsonNode attachment : attachments) {
            String name = attachment.path("name").asText("");
            String mimeType = attachment.path("mimeType").asText("");
            String base64 = attachment.path("base64").asText("");
            result.addAll(documentImageConverter.toImageDataUrls(name, mimeType, base64));
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // 私有：大智部 OCR 分页处理
    // -------------------------------------------------------------------------

    private JsonNode callDazhiOcrWithAttachments(ObjectNode request) throws Exception {
        JsonNode attachments = request.path("attachments");
        ArrayNode pages = objectMapper.createArrayNode();
        if (!attachments.isArray()) {
            return pages;
        }
        for (JsonNode attachment : attachments) {
            String picName = attachment.path("name").asText("file");
            if (picName.isBlank()) picName = "file";
            String mimeType = attachment.path("mimeType").asText("");
            String base64File = attachment.path("base64").asText("");
            List<String> imageDataUrls = documentImageConverter.toImageDataUrls(picName, mimeType, base64File);
            for (String dataUrl : imageDataUrls) {
                String base64 = dataUrl;
                int idx = dataUrl.indexOf(',');
                if (idx >= 0 && idx + 1 < dataUrl.length()) {
                    base64 = dataUrl.substring(idx + 1);
                }
                ObjectNode pageReq = objectMapper.createObjectNode();
                pageReq.put("picName", picName);
                pageReq.put("imageBase64", base64);
                JsonNode node = objectMapper.readTree(
                        dazhiOcrTool.execute(objectMapper.writeValueAsString(pageReq)).toJsonString());
                pages.add(node);
            }
        }
        List<String> pageTexts = new ArrayList<>();
        for (JsonNode page : pages) {
            String t = extractImg2TextContent(page);
            if (!t.isBlank()) pageTexts.add(t);
        }
        ObjectNode merged = objectMapper.createObjectNode();
        merged.put("success", true);
        merged.put("pageCount", pages.size());
        merged.put("content", String.join("\n\n", pageTexts));
        merged.set("pages", pages);
        return merged;
    }

    // -------------------------------------------------------------------------
    // 私有：文本提取 & 视图构建 & 引擎路由
    // -------------------------------------------------------------------------

    /**
     * 从各引擎响应节点中提取可读文本。
     * 支持：img2text（OpenAI choices / data.text）、大智部 OCR（resultMsg.picList.picContent.contents）。
     */
    private String extractImg2TextContent(JsonNode toolNode) {
        String result = toolNode.path("result").asText("");
        if (!result.isBlank()) return result;

        JsonNode response = toolNode.path("response");

        JsonNode choices = response.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            String content = choices.get(0).path("message").path("content").asText("");
            if (!content.isBlank()) return content;
        }

        String text = response.path("data").path("text").asText("");
        if (!text.isBlank()) return text;

        JsonNode picList = response.path("resultMsg").path("picList");
        if (picList.isArray() && !picList.isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (JsonNode pic : picList) {
                JsonNode contents = pic.path("picContent").path("contents");
                if (contents.isArray()) {
                    for (JsonNode line : contents) {
                        String lineText = line.asText("").trim();
                        if (!lineText.isBlank()) lines.add(lineText);
                    }
                }
            }
            if (!lines.isEmpty()) return String.join("\n", lines);
        }
        return "";
    }

    private ObjectNode buildImg2TextView(JsonNode toolNode) {
        ObjectNode view = objectMapper.createObjectNode();
        view.put("action", toolNode.path("action").asText("img2text"));
        view.put("success", toolNode.path("success").asBoolean(false));
        view.put("httpStatus", toolNode.path("httpStatus").asInt(0));
        view.put("content", extractImg2TextContent(toolNode));
        view.set("response", toolNode.path("response"));
        return view;
    }

    private ObjectNode buildDazhiOcrView(JsonNode resultNode) {
        ObjectNode view = objectMapper.createObjectNode();
        view.put("engine", "dazhi-ocr");
        view.put("success", resultNode.path("success").asBoolean(false));
        view.put("content", resultNode.path("content").asText(""));
        view.set("result", resultNode);
        return view;
    }

    /**
     * 由 LLM 根据用户语义描述，在 img2text 与 dazhi-ocr 之间做路由决策。
     */
    private String decideOcrEngine(String text, boolean hasAttachments) {
        try {
            String prompt = """
                    你是一个路由控制器，负责在两个能力之间做选择：
                    1）img2text：适合"阅读/理解/总结图片或文档内容、判断是否加盖公章及公章内容、提炼要点、回答关于文档的问题"等语义理解场景。
                    2）dazhi-ocr：适合"纯OCR识别文本/字段，例如身份证、营业执照、票据等结构化文字提取"，不需要复杂理解。

                    请根据用户输入的文字描述和是否上传了附件，选择最合适的一个引擎。
                    只返回一个单词：img2text 或 dazhi-ocr，不要输出其他任何解释。

                    用户是否上传了附件：%s
                    用户输入内容：%s
                    """.formatted(hasAttachments ? "是" : "否", text == null ? "" : text.trim());
            String ans = llmService.simpleChat(prompt);
            String lower = ans == null ? "" : ans.toLowerCase();
            if (lower.contains("img2text")) return "img2text";
            if (lower.contains("dazhi-ocr") || lower.contains("dazhi") || lower.contains("ocr")) return "dazhi-ocr";
        } catch (Exception e) {
            log.warn("[MultimodalService] 引擎路由决策失败，降级到 img2text: {}", e.getMessage());
        }
        return hasAttachments ? "img2text" : "dazhi-ocr";
    }

    private record OcrPlan(String engine, String strategy) {
    }
}
