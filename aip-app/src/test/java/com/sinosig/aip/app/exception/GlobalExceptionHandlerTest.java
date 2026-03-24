package com.sinosig.aip.app.exception;

import com.sinosig.aip.common.core.exception.LlmException;
import com.sinosig.aip.common.core.exception.ToolExecutionException;
import com.sinosig.aip.common.core.response.ResponseResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.io.IOException;
import java.lang.reflect.Method;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    @Test
    void handleLlmException_shouldReturnLlmExceptionCode() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseResult<Void> result = handler.handleLlmException(new LlmException("llm down"));
        assertEquals(503, result.getCode());
    }

    @Test
    void handleToolException_shouldReturnToolExceptionCode() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseResult<Void> result = handler.handleToolException(new ToolExecutionException("toolA", "failed"));
        assertEquals(500, result.getCode());
    }

    @Test
    void handleConstraintViolationException_shouldReturnBadRequestWithFieldMessage() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("listTasks.page");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("必须大于等于 1");

        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseResult<Void> result = handler.handleConstraintViolationException(ex);

        assertEquals(400, result.getCode());
        assertEquals("参数校验失败", result.getMessage());
        assertNotNull(result.getValidationErrors());
        assertEquals(1, result.getValidationErrors().size());
        assertEquals("page", result.getValidationErrors().get(0).getField());
        assertTrue(result.getValidationErrors().get(0).getMessage().contains("必须大于等于 1"));
    }

    @Test
    void handleValidationException_shouldNormalizeRequestPrefix() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "request");
        bindingResult.addError(new FieldError("request", "request.taskName", "不能为空"));

        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummy", String.class);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(new org.springframework.core.MethodParameter(method, 0), bindingResult);

        ResponseResult<Void> result = handler.handleValidationException(ex);
        assertEquals(400, result.getCode());
        assertNotNull(result.getValidationErrors());
        assertEquals("taskName", result.getValidationErrors().get(0).getField());
    }

    @SuppressWarnings("unused")
    private void dummy(String arg) {
        // only used for constructing MethodParameter in test
    }

    @Test
    void handleHttpMessageNotWritable_shouldReturnNoContentWhenBrokenPipe() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpMessageNotWritableException ex = new HttpMessageNotWritableException(
                "Could not write JSON", new IOException("Broken pipe"));
        ResponseEntity<Void> re = handler.handleHttpMessageNotWritable(ex);
        assertEquals(HttpStatus.NO_CONTENT, re.getStatusCode());
    }

    @Test
    void handleHttpMessageNotWritable_shouldReturn500WhenNotClientAbort() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        HttpMessageNotWritableException ex = new HttpMessageNotWritableException(
                "Serialization bug", new IllegalStateException("no writer"));
        ResponseEntity<Void> re = handler.handleHttpMessageNotWritable(ex);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, re.getStatusCode());
    }
}

