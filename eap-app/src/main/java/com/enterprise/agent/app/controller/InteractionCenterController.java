package com.enterprise.agent.app.controller;

import com.enterprise.agent.common.core.response.ResponseResult;
import com.enterprise.agent.impl.interaction.ConversationSession;
import com.enterprise.agent.impl.interaction.InteractionCenterAgent;
import com.enterprise.agent.impl.interaction.InteractionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * InteractionCenterController - AI 交互中心 REST API
 * <p>
 * 统一对话入口，支持多轮对话、会话管理和 Agent 路由。
 */
@Tag(name = "AI 交互中心", description = "多轮对话统一入口，支持意图识别自动路由到专业 Agent")
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class InteractionCenterController {

    private final InteractionCenterAgent interactionCenterAgent;
    private final ConversationSession conversationSession;

    @Operation(
            summary = "发送对话消息",
            description = "接收用户自然语言输入，自动识别意图并路由到 PLANNER / KNOWLEDGE / INSIGHT / GENERAL"
    )
    @PostMapping
    public ResponseResult<InteractionResult> chat(@Valid @RequestBody ChatRequest request) {
        InteractionResult result = interactionCenterAgent.chat(request.getSessionId(), request.getMessage());
        return ResponseResult.success(result);
    }

    @Operation(summary = "创建新会话", description = "创建一个新的对话会话，返回 sessionId 供后续使用")
    @PostMapping("/session")
    public ResponseResult<Map<String, String>> createSession() {
        String sessionId = conversationSession.createSession();
        return ResponseResult.success(Map.of("sessionId", sessionId));
    }

    @Operation(summary = "清除会话历史", description = "删除指定会话的所有历史消息，但会话本身不删除")
    @DeleteMapping("/session/{sessionId}")
    public ResponseResult<Void> clearSession(
            @Parameter(description = "会话 ID") @PathVariable String sessionId) {
        conversationSession.clearSession(sessionId);
        return ResponseResult.success();
    }

    @Operation(summary = "获取会话历史", description = "获取指定会话的全部历史消息列表")
    @GetMapping("/session/{sessionId}/history")
    public ResponseResult<List<ConversationSession.Message>> getHistory(
            @Parameter(description = "会话 ID") @PathVariable String sessionId) {
        return ResponseResult.success(conversationSession.getHistory(sessionId));
    }

    @Data
    public static class ChatRequest {
        @NotBlank(message = "sessionId 不能为空")
        private String sessionId;

        @NotBlank(message = "消息内容不能为空")
        @Size(max = 2000, message = "消息内容不超过2000字符")
        private String message;
    }
}
