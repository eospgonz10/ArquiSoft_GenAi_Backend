package com.arquisoft.genai.infrastructure.ai.support;

import com.arquisoft.genai.application.exception.AiProviderException;
import com.arquisoft.genai.application.exception.AiRateLimitException;
import com.arquisoft.genai.application.exception.AiTransientException;
import com.arquisoft.genai.application.model.ArchitectureInput;
import com.arquisoft.genai.application.model.ArchitectureOutput;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpStatusCodeException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Slf4j
public abstract class AiProviderSupport {

    protected abstract ObjectMapper objectMapper();

    protected <T> T executeWithRetry(String providerName, int maxRetries, Supplier<T> action) {
        int allowedRetries = Math.max(0, maxRetries);
        int attempt = 0;

        while (true) {
            try {
                return action.get();
            } catch (AiTransientException ex) {
                if (attempt >= allowedRetries) {
                    throw ex;
                }
                long sleepMillis = backoffMillis(attempt);
                log.warn("{} transient failure on attempt {}/{}. Retrying in {} ms: {}",
                        providerName, attempt + 1, allowedRetries + 1, sleepMillis, ex.getMessage());
                sleep(sleepMillis);
                attempt++;
            }
        }
    }

    protected ArchitectureOutput parseStandardResponse(ObjectMapper objectMapper, String rawContent, String providerName) {
        String json = stripMarkdownFences(rawContent);
        try {
            JsonNode node = objectMapper.readTree(json);
            return ArchitectureOutput.builder()
                    .style(node.path("style").asText(""))
                    .qualityAttributes(parseList(node.path("qualityAttributes")))
                    .diagrams(parseMap(node.path("diagrams")))
                    .diagramUrls(null)
                    .documentation(node.path("documentation").asText(""))
                    .techStack(parseList(node.path("techStack")))
                    .decisions(parseList(node.path("decisions")))
                    .build();
        } catch (Exception e) {
            throw new AiProviderException("Failed to parse " + providerName + " JSON response: " + e.getMessage(), e);
        }
    }

    protected String buildUserPrompt(ArchitectureInput input) {
        StringBuilder sb = new StringBuilder();
        sb.append("Domain: ").append(input.getDomain()).append('\n');
        sb.append("Goal: generate a complete software architecture package from the provided documentation, ")
            .append("covering UML use case, component, class, C4 context, C4 container, C4 component, and one key sequence diagram.\n");
        if (input.getQualityAttributes() != null && !input.getQualityAttributes().isEmpty()) {
            sb.append("Quality attributes: ")
                    .append(String.join(", ", input.getQualityAttributes())).append('\n');
        }
        if (input.getTechStackConstraints() != null && !input.getTechStackConstraints().isEmpty()) {
            sb.append("Tech constraints: ")
                    .append(String.join(", ", input.getTechStackConstraints())).append('\n');
        }
        if (input.getNaturalLanguageDescription() != null && !input.getNaturalLanguageDescription().isBlank()) {
            sb.append("Description: ").append(input.getNaturalLanguageDescription()).append('\n');
        }
        sb.append('\n')
                .append("Output requirements:\n")
                .append("- Return ONLY valid JSON with fields: style, qualityAttributes, diagrams, documentation, techStack, decisions.\n")
                .append("- diagrams must be an object where each key is the diagram name and each value is PlantUML code.\n")
                .append("- Generate at least these diagrams: C4-Context, C4-Container, C4-Component, Use-Case, Component-Diagram, Class-Diagram, Sequence-Primary-Flow.\n")
                .append("- Prefer C4-PlantUML includes for C4 diagrams and standard PlantUML syntax for UML diagrams.\n")
                .append("- Make diagrams specific to the domain, naming concrete actors, services, data stores, interfaces, and relationships.\n")
                .append("- Use the documentation and constraints to infer realistic components, responsibilities, dependencies, and quality attribute trade-offs.\n")
                .append("- Keep the architecture internally consistent across all diagrams.\n")
                .append("- No markdown fences and no explanatory text outside JSON.\n");
        return sb.toString();
    }

    protected String stripMarkdownFences(String content) {
        String trimmed = content == null ? "" : content.strip();
        if (trimmed.startsWith("```") ) {
            trimmed = trimmed.replaceFirst("```[a-zA-Z]*\\n?", "");
            trimmed = trimmed.replaceAll("\\n?```$", "").strip();
        }
        return trimmed;
    }

    protected List<String> parseList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> list.add(item.asText()));
        }
        return list;
    }

    protected Map<String, String> parseMap(JsonNode node) {
        Map<String, String> map = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(entry -> map.put(entry.getKey(), entry.getValue().asText()));
        }
        return map;
    }

    protected AiRateLimitException buildRateLimitException(String providerName, HttpStatusCodeException ex) {
        HttpHeaders headers = ex.getResponseHeaders();
        Integer retryAfterSeconds = parseRetryAfter(headers);
        Map<String, String> rateLimitHeaders = extractRelevantHeaders(headers);
        String responseBody = ex.getResponseBodyAsString();

        StringBuilder message = new StringBuilder();
        message.append(providerName).append(" rate limit exceeded (429)");
        if (retryAfterSeconds != null) {
            message.append(". Retry after approximately ").append(retryAfterSeconds).append(" seconds");
        }
        message.append(". Check plan/model rate limits and request burst behavior.");

        return new AiRateLimitException(providerName, ex.getStatusCode().value(), message.toString(),
                retryAfterSeconds, rateLimitHeaders, responseBody, ex);
    }

    protected AiTransientException buildTransientException(String providerName, String message, Throwable cause) {
        return new AiTransientException(providerName + " transient failure: " + message, cause);
    }

    private long backoffMillis(int attempt) {
        long base = 250L * (1L << Math.min(attempt, 4));
        long jitter = ThreadLocalRandom.current().nextLong(0L, 150L);
        return Math.min(base + jitter, Duration.ofSeconds(3).toMillis());
    }

    private void sleep(long sleepMillis) {
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("Interrupted while waiting before AI retry", e);
        }
    }

    private Integer parseRetryAfter(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null || retryAfter.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(retryAfter.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Map<String, String> extractRelevantHeaders(HttpHeaders headers) {
        Map<String, String> details = new LinkedHashMap<>();
        if (headers == null) {
            return details;
        }
        captureHeader(headers, details, HttpHeaders.RETRY_AFTER);
        captureHeader(headers, details, "x-request-id");
        captureHeader(headers, details, "x-ratelimit-limit-requests");
        captureHeader(headers, details, "x-ratelimit-remaining-requests");
        captureHeader(headers, details, "x-ratelimit-reset-requests");
        captureHeader(headers, details, "x-ratelimit-limit-tokens");
        captureHeader(headers, details, "x-ratelimit-remaining-tokens");
        captureHeader(headers, details, "x-ratelimit-reset-tokens");
        return details;
    }

    private void captureHeader(HttpHeaders headers, Map<String, String> target, String headerName) {
        String value = headers.getFirst(headerName);
        if (value != null && !value.isBlank()) {
            target.put(headerName.toLowerCase(Locale.ROOT), value);
        }
    }
}