package com.sinosig.aip.business.chat.controller.interaction;

import com.sinosig.aip.business.chat.ConversationSession;
import com.sinosig.aip.business.chat.InteractionCenterAgent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class InteractionCenterControllerTimestampTest {

    @Test
    void extractTimestamp_shouldUseMetadataLocalDateTime() throws Exception {
        InteractionCenterController controller = new InteractionCenterController(
                mock(InteractionCenterAgent.class),
                mock(ConversationSession.class),
                mock(ChatMemory.class)
        );
        LocalDateTime expected = LocalDateTime.of(2026, 3, 23, 20, 30, 0);
        Message message = messageWithMetadata(Map.of("timestamp", expected));

        Method method = InteractionCenterController.class
                .getDeclaredMethod("extractTimestamp", Message.class);
        method.setAccessible(true);
        LocalDateTime actual = (LocalDateTime) method.invoke(controller, message);

        assertEquals(expected, actual);
    }

    @Test
    void extractTimestamp_shouldFallbackToNowWhenMetadataMissing() throws Exception {
        InteractionCenterController controller = new InteractionCenterController(
                mock(InteractionCenterAgent.class),
                mock(ConversationSession.class),
                mock(ChatMemory.class)
        );
        Message message = messageWithMetadata(Map.of());
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        Method method = InteractionCenterController.class
                .getDeclaredMethod("extractTimestamp", Message.class);
        method.setAccessible(true);
        LocalDateTime actual = (LocalDateTime) method.invoke(controller, message);

        assertNotNull(actual);
        assertTrue(actual.isAfter(before));
    }

    @Test
    void extractTimestamp_shouldParseInstantString() throws Exception {
        InteractionCenterController controller = new InteractionCenterController(
                mock(InteractionCenterAgent.class),
                mock(ConversationSession.class),
                mock(ChatMemory.class)
        );
        String instantText = "2026-03-23T12:00:00Z";
        Message message = messageWithMetadata(Map.of("createdAt", instantText));
        LocalDateTime expected = LocalDateTime.ofInstant(Instant.parse(instantText), ZoneId.systemDefault());

        Method method = InteractionCenterController.class
                .getDeclaredMethod("extractTimestamp", Message.class);
        method.setAccessible(true);
        LocalDateTime actual = (LocalDateTime) method.invoke(controller, message);

        assertEquals(expected, actual);
    }

    private Message messageWithMetadata(Map<String, Object> metadata) {
        return (Message) Proxy.newProxyInstance(
                Message.class.getClassLoader(),
                new Class[]{Message.class},
                (proxy, method, args) -> {
                    if ("getMetadata".equals(method.getName())) {
                        return metadata;
                    }
                    if ("getText".equals(method.getName())) {
                        return "test";
                    }
                    if ("getMessageType".equals(method.getName())) {
                        return "USER";
                    }
                    if ("toString".equals(method.getName())) {
                        return "MockMessage";
                    }
                    return null;
                }
        );
    }
}

