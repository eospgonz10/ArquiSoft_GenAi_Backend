package com.arquisoft.genai.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate bean configured with connection and read timeouts.
 * Uses SimpleClientHttpRequestFactory directly since RestTemplateBuilder
 * removed timeout methods in Spring Boot 3.3+.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30_000); // 30 seconds
        factory.setReadTimeout(90_000);    // 90 seconds
        return new RestTemplate(factory);
    }
}
