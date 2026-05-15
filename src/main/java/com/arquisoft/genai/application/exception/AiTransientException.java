package com.arquisoft.genai.application.exception;

public class AiTransientException extends AiProviderException {
    public AiTransientException(String message) {
        super(message);
    }

    public AiTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}