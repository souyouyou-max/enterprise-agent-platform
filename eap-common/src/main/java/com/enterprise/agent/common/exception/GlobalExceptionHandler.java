package com.enterprise.agent.common.exception;

import com.enterprise.agent.common.core.exception.AgentException;
import com.enterprise.agent.common.core.exception.LlmException;
import com.enterprise.agent.common.core.exception.ToolExecutionException;
import com.enterprise.agent.common.core.response.ResponseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 统一全局异常处理器（由 eap-common 自动配置，各微服务无需重复定义）
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 是否在响应中返回 detail（便于联调排错，生产建议关闭）
     */
    @Value("${eap.error.include-detail:false}")
    private boolean includeDetail;

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

    @ExceptionHandler(ToolExecutionException.class)
    public ResponseResult<Void> handleToolException(ToolExecutionException e) {
        log.error("[ExceptionHandler] ToolExecutionException: tool={}, msg={}", e.getToolName(), e.getMessage());
        return ResponseResult.error(500, e.getMessage());
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
        String errorId = UUID.randomUUID().toString();
        log.error("[ExceptionHandler][errorId={}] 未处理异常: {}", errorId, e.getMessage(), e);
        String detail = includeDetail ? (e.getClass().getName() + ": " + e.getMessage()) : null;
        return ResponseResult.error(500, "服务器内部错误", errorId, detail);
    }
}
