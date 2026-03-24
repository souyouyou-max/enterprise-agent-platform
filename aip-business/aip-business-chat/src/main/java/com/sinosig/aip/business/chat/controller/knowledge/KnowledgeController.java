package com.sinosig.aip.business.chat.controller.knowledge;

import com.sinosig.aip.common.core.response.ResponseResult;
import com.sinosig.aip.engine.rag.knowledge.entity.KnowledgeDocument;
import com.sinosig.aip.engine.rag.knowledge.service.KnowledgeIndexService;
import com.sinosig.aip.engine.rag.knowledge.service.KnowledgeQaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 企业知识问答 REST API（RAG）
 */
@Tag(name = "知识问答（RAG）", description = "文档录入、语义检索、LLM 问答")
@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeIndexService knowledgeIndexService;
    private final KnowledgeQaService knowledgeQaService;

    @Operation(summary = "录入知识文档", description = "对文档向量化后存入知识库")
    @PostMapping("/index")
    public ResponseResult<KnowledgeDocument> indexDocument(@Valid @RequestBody IndexRequest request) {
        KnowledgeDocument doc = knowledgeIndexService.indexDocument(
                request.getTitle(), request.getContent(), request.getCategory());
        return ResponseResult.success(doc);
    }

    @Operation(summary = "知识问答", description = "语义检索相关文档，调用 LLM 生成答案（RAG）")
    @PostMapping("/ask")
    public ResponseResult<String> ask(@Valid @RequestBody AskRequest request) {
        String answer = knowledgeQaService.answer(request.getQuestion());
        return ResponseResult.success(answer);
    }

    @Operation(summary = "查询文档列表", description = "返回知识库中所有已录入文档")
    @GetMapping("/documents")
    public ResponseResult<List<KnowledgeDocument>> listDocuments() {
        return ResponseResult.success(knowledgeIndexService.listAll());
    }

    @Data
    public static class IndexRequest {
        @NotBlank(message = "标题不能为空")
        @Size(max = 200, message = "标题不超过200字符")
        private String title;

        @NotBlank(message = "内容不能为空")
        private String content;

        @Size(max = 100, message = "分类不超过100字符")
        private String category;
    }

    @Data
    public static class AskRequest {
        @NotBlank(message = "问题不能为空")
        @Size(max = 1000, message = "问题不超过1000字符")
        private String question;
    }
}
