package com.enterprise.agent.business.chat.controller;

import com.enterprise.agent.business.chat.service.MultimodalService;
import com.enterprise.agent.common.core.response.ResponseResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
public class EnterpriseToolController {

    private final ObjectMapper objectMapper;
    private final MultimodalService multimodalService;

    @Operation(summary = "正言图文理解（img2text）",
            description = "请求体包含 text、attachments（可选）；附件自动转图片后按批调用。")
    @PostMapping("/semantics/img2text")
    public ResponseResult<Object> img2Text(@RequestBody Map<String, Object> body) throws Exception {
        ObjectNode request = objectMapper.convertValue(body, ObjectNode.class);
        return ResponseResult.success(
                objectMapper.convertValue(multimodalService.img2Text(request), Object.class));
    }

    @Operation(summary = "智能 OCR 路由（auto-ocr）",
            description = "由 LLM 根据用户意图自动选择正言 img2text 或大智部通用 OCR；两种引擎均返回统一 content 字段。")
    @PostMapping("/semantics/auto-ocr")
    public ResponseResult<Object> autoOcr(@RequestBody Map<String, Object> body) throws Exception {
        ObjectNode request = objectMapper.convertValue(body, ObjectNode.class);
        return ResponseResult.success(
                objectMapper.convertValue(multimodalService.autoOcr(request), Object.class));
    }

    @Operation(summary = "大智部通用 OCR",
            description = "支持 imageBase64 单图或 attachments 多页文件；多页自动拆分并聚合 content。")
    @PostMapping("/ocr/general")
    public ResponseResult<Object> dazhiOcrGeneral(@RequestBody Map<String, Object> body) throws Exception {
        ObjectNode request = objectMapper.convertValue(body, ObjectNode.class);
        return ResponseResult.success(
                objectMapper.convertValue(multimodalService.dazhiOcrGeneral(request), Object.class));
    }
}
