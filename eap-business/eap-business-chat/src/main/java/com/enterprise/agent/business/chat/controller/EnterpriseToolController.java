package com.enterprise.agent.business.chat.controller;

import com.enterprise.agent.business.chat.service.MultimodalService;
import com.enterprise.agent.business.chat.service.OcrRecognitionRequest;
import com.enterprise.agent.business.chat.service.OcrRecognitionService;
import com.enterprise.agent.data.entity.OcrFileMain;
import com.enterprise.agent.common.core.response.ResponseResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.Map;

/**
 * 多模态工具 API。
 * 负责图文理解（img2text）、智能 OCR 路由、大智部通用 OCR、正言专业问答等能力的 HTTP 入口。
 * 仅做请求解析与响应封装，业务逻辑全部委托给 {@link MultimodalService}。
 */
@Tag(name = "多模态工具 API", description = "图文理解、OCR 识别、专业问答等多模态能力")
@RestController
@RequestMapping("/api/v1/enterprise")
@RequiredArgsConstructor
@Slf4j
public class EnterpriseToolController {

    private final ObjectMapper objectMapper;
    private final MultimodalService multimodalService;
    private final OcrRecognitionService ocrRecognitionService;

    @Operation(summary = "正言图文理解（img2text）",
            description = "请求体包含 text、attachments（可选）；附件自动转图片后按批调用。")
    @PostMapping("/semantics/img2text")
    public ResponseResult<Object> img2Text(@RequestBody Map<String, Object> body) throws Exception {
        ObjectNode request = objectMapper.convertValue(body, ObjectNode.class);
        OcrFileMain main = ocrRecognitionService.recognize(
                buildOcrRequest(request, "ZHENGYAN_MULTIMODAL"));
        return ResponseResult.success(
                objectMapper.convertValue(buildImg2TextView(main), Object.class));
    }

    @Operation(summary = "智能 OCR 路由（auto-ocr）",
            description = "由 LLM 根据用户意图自动选择正言 img2text 或大智部通用 OCR；两种引擎均返回统一 content 字段。")
    @PostMapping("/semantics/auto-ocr")
    public ResponseResult<Object> autoOcr(@RequestBody Map<String, Object> body) throws Exception {
        ObjectNode request = objectMapper.convertValue(body, ObjectNode.class);
        ObjectNode view = multimodalService.autoOcr(request);

        // autoOcr 只负责返回识别结果给前端，这里额外做落库（避免再次调用引擎）
        try {
            String engine = view.path("engine").asText("");
            String source;
            ObjectNode engineResult;
            if ("img2text".equalsIgnoreCase(engine)) {
                source = "ZHENGYAN_MULTIMODAL";
                // zhengyan persistence 需要 response/content/success，engine=view 本身即可
                engineResult = view;
            } else if ("dazhi-ocr".equalsIgnoreCase(engine)) {
                source = "DAZHI_OCR";
                // dazhi-ocr persistence 需要内部 result（与 recognizeByDazhi 输入一致）
                engineResult = view.path("result").isMissingNode() ? view : (ObjectNode) view.path("result");
            } else {
                source = "";
                engineResult = view;
            }

            if (source != null && !source.isBlank()) {
                ocrRecognitionService.recognizeFromResult(
                        buildOcrRequest(request, source),
                        engineResult);
            }
        } catch (Exception e) {
            // 落库失败不影响前端返回（原有行为保持）
            log.warn("[EnterpriseToolController] auto-ocr 落库失败: {}", e.getMessage(), e);
        }

        return ResponseResult.success(objectMapper.convertValue(view, Object.class));
    }

    @Operation(summary = "大智部通用 OCR",
            description = "支持 imageBase64 单图或 attachments 多页文件；多页自动拆分并聚合 content。")
    @PostMapping("/ocr/general")
    public ResponseResult<Object> dazhiOcrGeneral(@RequestBody Map<String, Object> body) throws Exception {
        ObjectNode request = objectMapper.convertValue(body, ObjectNode.class);
        OcrFileMain main = ocrRecognitionService.recognize(
                buildOcrRequest(request, "DAZHI_OCR"));
        return ResponseResult.success(
                objectMapper.convertValue(buildDazhiOcrView(main), Object.class));
    }

    private OcrRecognitionRequest buildOcrRequest(ObjectNode request, String source) {
        // 业务端可直接传入 businessNo / fileName / appCode 等字段；前端未传时，这里兜底生成。
        String businessNo = request.path("businessNo").asText("");
        if (businessNo == null || businessNo.isBlank()) {
            businessNo = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        }

        String fileName = request.path("fileName").asText("");
        if (fileName == null || fileName.isBlank()) {
            // 优先从 attachments 提取文件名
            fileName = extractFirstAttachmentName(request);
        }
        if (fileName == null || fileName.isBlank()) {
            fileName = "ocr_file";
        }

        String fileType = request.path("fileType").asText("");
        if (fileType == null || fileType.isBlank()) {
            String mimeType = extractFirstAttachmentMimeType(request);
            if (mimeType != null && !mimeType.isBlank()) {
                fileType = mimeType;
            }
        }

        Long fileSize = request.has("fileSize") && !request.get("fileSize").isNull()
                ? request.get("fileSize").asLong()
                : null;
        String filePath = request.path("filePath").asText("");
        if (filePath != null && filePath.isBlank()) filePath = null;

        String appCode = request.path("appCode").asText("");
        if (appCode != null && appCode.isBlank()) appCode = null;

        String extraInfo = null;
        if (request.has("extraInfo") && !request.get("extraInfo").isNull()) {
            extraInfo = request.get("extraInfo").isTextual()
                    ? request.get("extraInfo").asText()
                    : request.get("extraInfo").toString();
        }

        // prompt：优先用 text（img2text 场景更常用）；也支持显式 prompt 字段。
        String prompt = request.path("prompt").asText("");
        if (prompt == null || prompt.isBlank()) {
            prompt = request.path("text").asText("");
        }
        if (prompt != null && prompt.isBlank()) prompt = null;

        return OcrRecognitionRequest.builder()
                .businessNo(businessNo)
                .source(source)
                .fileName(fileName)
                .fileType(fileType)
                .fileSize(fileSize)
                .filePath(filePath)
                .appCode(appCode)
                .extraInfo(extraInfo)
                .prompt(prompt)
                // engineRequest：直接透传原始请求体给 MultimodalService，便于复用原来的引擎交互逻辑
                .engineRequest(request.deepCopy())
                .build();
    }

    private String extractFirstAttachmentName(ObjectNode request) {
        if (!request.has("attachments") || !request.get("attachments").isArray()) return "";
        if (request.get("attachments").size() == 0) return "";
        return request.get("attachments").get(0).path("name").asText("");
    }

    private String extractFirstAttachmentMimeType(ObjectNode request) {
        if (!request.has("attachments") || !request.get("attachments").isArray()) return "";
        if (request.get("attachments").size() == 0) return "";
        return request.get("attachments").get(0).path("mimeType").asText("");
    }

    private ObjectNode buildImg2TextView(OcrFileMain main) {
        boolean ok = main.getOcrStatus() != null && main.getOcrStatus().equalsIgnoreCase("SUCCESS");
        ObjectNode view = objectMapper.createObjectNode();
        view.put("action", "img2text");
        view.put("success", ok);
        view.put("httpStatus", 200);
        view.put("content", main.getOcrResult() == null ? "" : main.getOcrResult());
        view.set("response", objectMapper.createObjectNode());
        return view;
    }

    private ObjectNode buildDazhiOcrView(OcrFileMain main) {
        boolean ok = main.getOcrStatus() != null && main.getOcrStatus().equalsIgnoreCase("SUCCESS");
        ObjectNode view = objectMapper.createObjectNode();
        view.put("engine", "dazhi-ocr");
        view.put("success", ok);
        view.put("content", main.getOcrResult() == null ? "" : main.getOcrResult());
        view.set("result", objectMapper.createObjectNode());
        return view;
    }
}
