package com.enterprise.agent.common.core.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResponseResult<T> {

    private int code;
    private String message;
    private T data;
    private LocalDateTime timestamp;

    private ResponseResult() {
        this.timestamp = LocalDateTime.now();
    }

    public static <T> ResponseResult<T> success(T data) {
        ResponseResult<T> result = new ResponseResult<>();
        result.code = 200;
        result.message = "success";
        result.data = data;
        return result;
    }

    public static <T> ResponseResult<T> success() {
        return success(null);
    }

    public static <T> ResponseResult<T> error(int code, String message) {
        ResponseResult<T> result = new ResponseResult<>();
        result.code = code;
        result.message = message;
        return result;
    }

    public static <T> ResponseResult<T> error(String message) {
        return error(500, message);
    }

    public boolean isSuccess() {
        return this.code == 200;
    }
}
