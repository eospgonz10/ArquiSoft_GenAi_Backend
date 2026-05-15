package com.arquisoft.genai.application.exception;

import java.util.Collections;
import java.util.Map;

public class AiRateLimitException extends AiProviderException {

    private final String provider;
    private final int statusCode;
    private final Integer retryAfterSeconds;
    private final Map<String, String> rateLimitHeaders;
    private final String responseBody;

    public AiRateLimitException(String provider,
                                int statusCode,
                                String message,
                                Integer retryAfterSeconds,
                                Map<String, String> rateLimitHeaders,
                                String responseBody,
                                Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.statusCode = statusCode;
        this.retryAfterSeconds = retryAfterSeconds;
        this.rateLimitHeaders = rateLimitHeaders == null ? Collections.emptyMap() : Collections.unmodifiableMap(rateLimitHeaders);
        this.responseBody = responseBody;
    }

    public String getProvider() {
        return provider;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public Map<String, String> getRateLimitHeaders() {
        return rateLimitHeaders;
    }

    public String getResponseBody() {
        return responseBody;
    }
}