package com.sinosig.aip.scheduler.client.dto;

import lombok.Data;

/**
 * 通用响应包装（对应 aip-app ResponseResult）
 */
@Data
public class ResponseDTO<T> {
    private int code;
    private String message;
    private T data;
}
