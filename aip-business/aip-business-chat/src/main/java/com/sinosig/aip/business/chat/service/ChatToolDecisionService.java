package com.sinosig.aip.business.chat.service;

import com.sinosig.aip.business.chat.config.AipChatProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 对话工具启用判定服务：
 * 将“是否启用机构编排工具”的规则独立出来，减少 Agent 职责耦合。
 */
@Component
public class ChatToolDecisionService {

    // 机构编码常见格式：ORG001 / ORG_001 / ORG-001（大小写不敏感）
    private static final Pattern ORG_CODE_PATTERN =
            Pattern.compile("(?i)\\borg[-_]?\\d{2,}\\b");

    public boolean shouldUseOrgTools(String message, AipChatProperties properties) {
        if (message == null || message.isBlank()) {
            return false;
        }
        if (ORG_CODE_PATTERN.matcher(message).find()) {
            return true;
        }
        List<String> keywords = properties.getOrgToolKeywords();
        String lower = message.toLowerCase();
        return keywords != null && keywords.stream().anyMatch(lower::contains);
    }
}

