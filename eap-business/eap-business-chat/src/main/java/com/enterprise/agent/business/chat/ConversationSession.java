package com.enterprise.agent.business.chat;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * ConversationSession - 会话生命周期管理
 * <p>
 * 职责仅限于：
 * <ol>
 *   <li>生成 sessionId（{@link #createSession()}）</li>
 *   <li>注销会话（{@link #clearSession(String)}）</li>
 *   <li>判断会话是否存在（{@link #exists(String)}）</li>
 * </ol>
 * <p>
 * 消息历史由 Spring AI 的 {@code MessageChatMemoryAdvisor}（以 {@code conversation_id} 为键）
 * 统一管理，本类不再维护消息列表，避免双重存储。
 * <p>
 * 生产环境可在 {@code ChatClientConfig} 中将 ChatMemory 替换为 Redis 持久化实现，
 * 届时本类的 {@code sessions} Map 也可相应替换为 Redis TTL Key。
 */
@Slf4j
@Component
public class ConversationSession {

    /** 全局最多存活的会话数，超限时 FIFO 淘汰最旧的会话 */
    private static final int MAX_SESSION_COUNT = 1000;

    /** sessionId 存活集合（仅用于判断会话是否被创建过） */
    private final Map<String, LocalDateTime> sessions = new ConcurrentHashMap<>();

    /** 按创建顺序记录 sessionId，用于超限时 FIFO 淘汰 */
    private final ConcurrentLinkedDeque<String> sessionOrder = new ConcurrentLinkedDeque<>();

    /**
     * 创建新会话，返回自动生成的 sessionId。
     * 若当前会话数已达上限，先 FIFO 淘汰最旧的会话。
     */
    public String createSession() {
        String sessionId = UUID.randomUUID().toString();
        evictIfNecessary();
        sessions.put(sessionId, LocalDateTime.now());
        sessionOrder.addLast(sessionId);
        log.info("[ConversationSession] 创建会话: {}, 当前会话总数: {}", sessionId, sessions.size());
        return sessionId;
    }

    /**
     * 清除指定会话（同时通知调用方，Spring AI ChatMemory 侧也应清除对应 conversation_id）。
     */
    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
        sessionOrder.remove(sessionId);
        log.info("[ConversationSession] 清除会话: {}", sessionId);
    }

    /**
     * 会话是否存在
     */
    public boolean exists(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void evictIfNecessary() {
        while (sessions.size() >= MAX_SESSION_COUNT) {
            String oldest = sessionOrder.pollFirst();
            if (oldest == null) break;
            sessions.remove(oldest);
            log.warn("[ConversationSession] 会话数超限，淘汰最旧会话: {}", oldest);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DTO（保留用于 GET /session/{id}/history 接口的响应类型，实际数据由 ChatMemory 提供）
    // ─────────────────────────────────────────────────────────────────────────

    @Data
    public static class Message {
        private final String role;
        private final String content;
        private final LocalDateTime timestamp;
    }
}
