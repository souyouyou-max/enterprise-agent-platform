package com.enterprise.agent.common.core.exception;

public class AgentException extends RuntimeException {

    private final int code;

    public AgentException(String message) {
        super(message);
        this.code = 500;
    }

    public AgentException(int code, String message) {
        super(message);
        this.code = code;
    }

    public AgentException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static AgentException of(String message) {
        return new AgentException(message);
    }

    public static AgentException of(int code, String message) {
        return new AgentException(code, message);
    }
}
