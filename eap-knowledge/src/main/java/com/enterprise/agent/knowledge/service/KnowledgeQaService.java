package com.enterprise.agent.knowledge.service;

import com.enterprise.agent.common.ai.service.LlmService;
import com.enterprise.agent.knowledge.entity.KnowledgeDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识问答服务（RAG 核心）
 * 流程：问题语义检索 → 拼装 Prompt → LLM 生成答案
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeQaService {

    private static final String SYSTEM_PROMPT =
            "你是企业知识助手，根据以下参考文档回答用户问题。\n" +
            "如果文档中没有相关信息，请如实说明，不要编造。\n" +
            "回答时请引用文档标题，使答案可溯源。";

    private static final int DEFAULT_TOP_K = 3;

    private final KnowledgeIndexService knowledgeIndexService;
    private final LlmService llmService;

    /**
     * 知识问答：检索相关文档 → 拼装上下文 → 调用 LLM → 返回答案
     *
     * @param question 用户问题
     * @return LLM 生成的答案
     */
    public String answer(String question) {
        log.info("收到知识问答请求: {}", question);

        // 1. 语义检索相关文档
        List<KnowledgeDocument> relatedDocs =
                knowledgeIndexService.searchSimilar(question, DEFAULT_TOP_K);

        if (relatedDocs.isEmpty()) {
            log.warn("未检索到相关文档，直接调用 LLM 回答");
            return llmService.simpleChat(question);
        }

        // 2. 拼装参考文档上下文
        String context = relatedDocs.stream()
                .map(doc -> String.format("【%s】\n%s", doc.getTitle(), doc.getContent()))
                .collect(Collectors.joining("\n\n---\n\n"));

        // 3. 构造 RAG Prompt
        String userMessage = String.format(
                "文档：\n%s\n\n问题：%s", context, question);

        log.debug("RAG Prompt 构建完成，参考文档数: {}", relatedDocs.size());

        // 4. 调用 LLM 生成答案
        return llmService.chatWithSystem(SYSTEM_PROMPT, userMessage);
    }
}
