package com.enterprise.agent.impl.interaction;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ConversationSession - 多轮对话会话管理（内存存储）
 * <p>
 * 每个会话维护一个消息列表，超出滑动窗口时自动裁剪旧消息。
 * 生产环境可替换为 Redis 持久化实现。
 */
@Slf4j
@Component
public class ConversationSession {

    /** 每个会话最多保留的消息条数（约10轮对话）*/
    private static final int MAX_HISTORY_SIZE = 20;

    private final Map<String, List<Message>> sessions = new ConcurrentHashMap<>();

    /**
     * 创建新会话，返回自动生成的 sessionId
     */
    public String createSession() {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new ArrayList<>());
        log.info("[ConversationSession] 创建会话: {}", sessionId);
        return sessionId;
    }

    /**
     * 向会话追加消息（会话不存在时自动创建）
     *
     * @param sessionId 会话 ID
     * @param role      消息角色："user" | "assistant"
     * @param content   消息内容
     */
    public void addMessage(String sessionId, String role, String content) {
        List<Message> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
        history.add(new Message(role, content, LocalDateTime.now()));
        // 滑动窗口：超出上限时移除最旧消息
        if (history.size() > MAX_HISTORY_SIZE) {
            history.subList(0, history.size() - MAX_HISTORY_SIZE).clear();
        }
    }

    /**
     * 获取会话历史消息列表
     */
    public List<Message> getHistory(String sessionId) {
        return List.copyOf(sessions.getOrDefault(sessionId, List.of()));
    }

    /**
     * 清除指定会话的所有历史
     */
    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
        log.info("[ConversationSession] 清除会话: {}", sessionId);
    }

    /**
     * 会话是否存在
     */
    public boolean exists(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    @Data
    public static class Message {
        private final String role;
        private final String content;
        private final LocalDateTime timestamp;
    }
}
