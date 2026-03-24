package com.sinosig.aip.business.chat.pipeline.executor;

import com.sinosig.aip.business.chat.pipeline.executor.config.ExecutorProperties;
import com.sinosig.aip.common.core.exception.ToolExecutionException;
import com.sinosig.aip.common.core.response.ToolResponse;
import com.sinosig.aip.tools.EnterpriseTool;
import com.sinosig.aip.tools.ToolRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolExecutionServiceTest {

    private static ExecutorProperties defaultProps() {
        return new ExecutorProperties();
    }

    @Test
    void executeToolWithRetry_shouldThrowWhenToolMissing() {
        ToolRegistry registry = mock(ToolRegistry.class);
        when(registry.getTool("missing")).thenReturn(null);

        ToolExecutionService service = new ToolExecutionService(registry, defaultProps());
        assertThrows(ToolExecutionException.class, () -> service.executeToolWithRetry("missing", "{}"));
    }

    @Test
    void executeToolWithRetry_shouldReturnRawJsonOnSuccess() {
        ToolRegistry registry = mock(ToolRegistry.class);
        EnterpriseTool tool = mock(EnterpriseTool.class);
        when(registry.getTool("sales")).thenReturn(tool);
        when(tool.execute("{}")).thenReturn(ToolResponse.fromRawJson("{\"ok\":true}"));

        ToolExecutionService service = new ToolExecutionService(registry, defaultProps());
        String result = service.executeToolWithRetry("sales", "{}");
        assertEquals("{\"ok\":true}", result);
    }

    @Test
    void executeToolWithRetry_shouldRespectConfiguredRetryCount() {
        ToolRegistry registry = mock(ToolRegistry.class);
        EnterpriseTool tool = mock(EnterpriseTool.class);
        when(registry.getTool("sales")).thenReturn(tool);
        when(tool.execute("{}")).thenThrow(new RuntimeException("down"));

        ExecutorProperties properties = defaultProps();
        properties.setMaxToolRetries(1);
        ToolExecutionService service = new ToolExecutionService(registry, properties);

        assertThrows(ToolExecutionException.class, () -> service.executeToolWithRetry("sales", "{}"));
        verify(tool, times(1)).execute("{}");
    }
}
