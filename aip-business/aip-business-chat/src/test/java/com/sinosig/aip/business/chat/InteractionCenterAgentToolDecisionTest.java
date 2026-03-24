package com.sinosig.aip.business.chat;

import com.sinosig.aip.business.chat.config.AipChatProperties;
import com.sinosig.aip.business.chat.service.ChatToolDecisionService;
import com.sinosig.aip.common.ai.service.LlmService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class InteractionCenterAgentToolDecisionTest {

    @Test
    void shouldUseTools_shouldMatchOrgCodePatternAndKeywords() throws Exception {
        InteractionCenterAgent agent = new InteractionCenterAgent(
                mock(LlmService.class),
                mock(ChatModel.class),
                null,
                null,
                null,
                new AipChatProperties(),
                new ChatToolDecisionService()
        );

        Method method = InteractionCenterAgent.class.getDeclaredMethod("shouldUseTools", String.class);
        method.setAccessible(true);

        boolean byOrgCode = (boolean) method.invoke(agent, "请分析 ORG001 的风险");
        boolean byKeyword = (boolean) method.invoke(agent, "请做风险透视分析");
        boolean plainNumber = (boolean) method.invoke(agent, "订单号 123456 请帮我解释");

        assertTrue(byOrgCode);
        assertTrue(byKeyword);
        assertFalse(plainNumber);
    }
}

