package com.enterprise.agent.common.core.exception;

public class LlmException extends AgentException {

    public LlmException(String message) {
        super(503, message);
    }

    public LlmException(String message, Throwable cause) {
        super(503, message, cause);
    }
}
