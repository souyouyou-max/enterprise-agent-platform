package com.enterprise.agent.service.insight.config;

import com.enterprise.agent.common.core.exception.AgentException;
import com.enterprise.agent.common.core.exception.LlmException;
import com.enterprise.agent.common.core.response.ResponseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AgentException.class)
    public ResponseResult<Void> handleAgentException(AgentException e) {
        log.warn("[ExceptionHandler] AgentException: code={}, msg={}", e.getCode(), e.getMessage());
        return ResponseResult.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(LlmException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ResponseResult<Void> handleLlmException(LlmException e) {
        log.error("[ExceptionHandler] LlmException: {}", e.getMessage());
        return ResponseResult.error(503, "LLM 服务暂时不可用: " + e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseResult<Void> handleValidationException(MethodArgumentNotValidException e) {
        String errors = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseResult.error(400, "参数校验失败: " + errors);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseResult<Void> handleGenericException(Exception e) {
        log.error("[ExceptionHandler] 未处理异常: {}", e.getMessage(), e);
        return ResponseResult.error(500, "服务器内部错误");
    }
}
