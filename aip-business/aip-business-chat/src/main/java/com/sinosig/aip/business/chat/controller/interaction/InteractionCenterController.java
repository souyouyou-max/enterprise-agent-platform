package com.sinosig.aip.business.chat.controller.interaction;

import com.sinosig.aip.business.chat.ConversationSession;
import com.sinosig.aip.business.chat.InteractionCenterAgent;
import com.sinosig.aip.business.chat.InteractionResult;
import com.sinosig.aip.common.core.response.ResponseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
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
    private final ChatMemory chatMemory;

    @Operation(
            summary = "发送对话消息",
            description = "接收用户自然语言输入，自动识别意图并路由到 PLANNER / KNOWLEDGE / INSIGHT / GENERAL"
    )
    @PostMapping
    public ResponseResult<InteractionResult> chat(@Valid @RequestBody ChatRequest request) {
        InteractionResult result = interactionCenterAgent.chat(request.getSessionId(), request.getMessage());
        return ResponseResult.success(result);
    }

    @Operation(
            summary = "流式对话（SSE）",
            description = "与普通对话相同逻辑，但以 Server-Sent Events 形式逐 token 推送，适合前端实时打字机效果"
    )
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody ChatRequest request) {
        return interactionCenterAgent.chatStream(request.getSessionId(), request.getMessage());
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
            @Parameter(description = "会话 ID") @PathVariable("sessionId") String sessionId) {
        conversationSession.clearSession(sessionId);
        chatMemory.clear(sessionId);
        return ResponseResult.success();
    }

    @Operation(summary = "获取会话历史", description = "获取指定会话的全部历史消息列表")
    @GetMapping("/session/{sessionId}/history")
    public ResponseResult<List<ConversationSession.Message>> getHistory(
            @Parameter(description = "会话 ID") @PathVariable("sessionId") String sessionId) {
        List<org.springframework.ai.chat.messages.Message> records = chatMemory.get(sessionId);
        List<ConversationSession.Message> history = new ArrayList<>(records.size());
        for (org.springframework.ai.chat.messages.Message record : records) {
            history.add(new ConversationSession.Message(
                    extractRole(record),
                    extractContent(record),
                    extractTimestamp(record)
            ));
        }
        return ResponseResult.success(history);
    }

    private String extractRole(org.springframework.ai.chat.messages.Message record) {
        try {
            Object mt = record.getClass().getMethod("getMessageType").invoke(record);
            return mt == null ? "UNKNOWN" : String.valueOf(mt);
        } catch (Exception ignored) {
            return "UNKNOWN";
        }
    }

    private String extractContent(org.springframework.ai.chat.messages.Message record) {
        try {
            Object v = record.getClass().getMethod("getText").invoke(record);
            if (v != null) return String.valueOf(v);
        } catch (Exception ignored) {
        }
        try {
            Object v = record.getClass().getMethod("getContent").invoke(record);
            if (v != null) return String.valueOf(v);
        } catch (Exception ignored) {
        }
        return String.valueOf(record);
    }

    private LocalDateTime extractTimestamp(org.springframework.ai.chat.messages.Message record) {
        try {
            Object metadataObj = record.getClass().getMethod("getMetadata").invoke(record);
            if (metadataObj instanceof Map<?, ?> metadata) {
                Object ts = metadata.get("timestamp");
                if (ts == null) {
                    ts = metadata.get("createdAt");
                }
                if (ts instanceof LocalDateTime ldt) {
                    return ldt;
                }
                if (ts instanceof Instant instant) {
                    return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
                }
                if (ts instanceof String s && !s.isBlank()) {
                    try {
                        return LocalDateTime.parse(s);
                    } catch (Exception ignored) {
                        try {
                            return LocalDateTime.ofInstant(Instant.parse(s), ZoneId.systemDefault());
                        } catch (Exception ignored2) {
                            // ignore parse failures and fallback below
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return LocalDateTime.now();
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
