package com.arquisoft.genai.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate with configurable timeouts.
 *
 * Environment variables (in .env):
 *   AI_CONNECT_TIMEOUT_SECONDS  — max time to establish TCP connection (default: 10s)
 *   AI_READ_TIMEOUT_SECONDS     — max time to wait for AI response    (default: 90s)
 *
 * WARNING: Do NOT set AI_READ_TIMEOUT_SECONDS=0 (infinite).
 * A value of 0 can cause threads to hang indefinitely if the remote server
 * accepts the connection but never sends a response.
 */
@Configuration
public class RestTemplateConfig {

    @Value("${ai.http.connect-timeout-seconds:10}")
    private int connectTimeoutSeconds;

    @Value("${ai.http.read-timeout-seconds:90}")
    private int readTimeoutSeconds;

    @Bean
    public RestTemplate restTemplate() {
        int connectMs = Math.max(connectTimeoutSeconds, 1) * 1_000;  // min 1s
        int readMs    = Math.max(readTimeoutSeconds,    30) * 1_000; // min 30s
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectMs);
        factory.setReadTimeout(readMs);
        return new RestTemplate(factory);
    }
}
