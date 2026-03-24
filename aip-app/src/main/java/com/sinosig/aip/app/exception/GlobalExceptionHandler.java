package com.sinosig.aip.app.exception;

import com.sinosig.aip.common.core.exception.AgentException;
import com.sinosig.aip.common.core.exception.LlmException;
import com.sinosig.aip.common.core.exception.ToolExecutionException;
import com.sinosig.aip.common.core.response.ResponseResult;
import com.sinosig.aip.common.core.util.ValidationFieldPathNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import jakarta.validation.ConstraintViolationException;
import java.io.IOException;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 统一全局异常处理器（由 aip-common 自动配置，各微服务无需重复定义）
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 是否在响应中返回 detail（便于联调排错，生产建议关闭）
     */
    @Value("${aip.error.include-detail:false}")
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
        return ResponseResult.error(e.getCode(), "LLM 服务暂时不可用: " + e.getMessage());
    }

    @ExceptionHandler(ToolExecutionException.class)
    public ResponseResult<Void> handleToolException(ToolExecutionException e) {
        log.error("[ExceptionHandler] ToolExecutionException: tool={}, msg={}", e.getToolName(), e.getMessage());
        return ResponseResult.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseResult<Void> handleValidationException(MethodArgumentNotValidException e) {
        List<ResponseResult.ValidationError> errors = e.getBindingResult().getFieldErrors().stream()
                .map(this::toValidationError)
                .toList();
        return ResponseResult.error(400, "参数校验失败", errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseResult<Void> handleConstraintViolationException(ConstraintViolationException e) {
        List<ResponseResult.ValidationError> errors = e.getConstraintViolations().stream()
                .map(v -> ResponseResult.ValidationError.of(
                        ValidationFieldPathNormalizer.normalize(String.valueOf(v.getPropertyPath())),
                        v.getMessage()))
                .collect(Collectors.toList());
        return ResponseResult.error(400, "参数校验失败", errors);
    }

    /**
     * 写响应体时失败：若为 Broken pipe / ClientAbort，属客户端已断开（超时、关页、网关断连），
     * 常见于返回体过大（如 OCR 多页 imageBase64）。不应按服务器 ERROR 刷屏。
     */
    @ExceptionHandler(HttpMessageNotWritableException.class)
    public ResponseEntity<Void> handleHttpMessageNotWritable(HttpMessageNotWritableException e) {
        if (isClientDisconnected(e)) {
            log.debug("[ExceptionHandler] 客户端已断开，中止响应写入: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        log.warn("[ExceptionHandler] 响应体序列化失败: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseResult<Void> handleGenericException(Exception e) {
        if (isClientDisconnected(e)) {
            log.debug("[ExceptionHandler] 客户端已断开: {}", e.getClass().getSimpleName());
            return ResponseResult.error(500, "服务器内部错误", "client-disconnected", null);
        }
        String errorId = UUID.randomUUID().toString();
        log.error("[ExceptionHandler][errorId={}] 未处理异常: {}", errorId, e.getMessage(), e);
        String detail = includeDetail ? (e.getClass().getName() + ": " + e.getMessage()) : null;
        return ResponseResult.error(500, "服务器内部错误", errorId, detail);
    }

    /**
     * 判断是否为「对端已关闭连接」，避免将正常断连记为 5xx 业务错误。
     */
    private static boolean isClientDisconnected(Throwable e) {
        Throwable c = e;
        while (c != null) {
            if (c instanceof AsyncRequestNotUsableException) {
                return true;
            }
            if (c instanceof IOException) {
                String m = c.getMessage();
                if (m != null && (m.contains("Broken pipe") || m.contains("Connection reset"))) {
                    return true;
                }
            }
            String name = c.getClass().getName();
            if (name.endsWith("ClientAbortException")) {
                return true;
            }
            c = c.getCause();
        }
        return false;
    }

    private ResponseResult.ValidationError toValidationError(FieldError e) {
        return ResponseResult.ValidationError.of(
                ValidationFieldPathNormalizer.normalize(e.getField()),
                e.getDefaultMessage());
    }
}
