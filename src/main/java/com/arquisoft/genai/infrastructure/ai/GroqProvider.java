package com.arquisoft.genai.infrastructure.ai;

import com.arquisoft.genai.application.exception.AiProviderException;
import com.arquisoft.genai.application.model.ArchitectureInput;
import com.arquisoft.genai.application.model.ArchitectureOutput;
import com.arquisoft.genai.application.port.AiProvider;
import com.arquisoft.genai.infrastructure.ai.dto.OpenAiMessage;
import com.arquisoft.genai.infrastructure.ai.dto.OpenAiRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Groq AI provider implementation (OpenAI-compatible API).
 * Activated when ai.provider=groq.
 *
 * Free tier: ~6,000 TPM for llama-3.1-8b-instant — no credit card required.
 * API docs: https://console.groq.com/docs/openai
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "groq")
@RequiredArgsConstructor
@Slf4j
public class GroqProvider implements AiProvider {

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    // Concise prompt (~250 tokens vs ~800 before) to stay within 6,000 TPM free limit
    private static final String SYSTEM_PROMPT =
            "You are a software architect. Given domain, quality attributes and tech constraints, " +
            "return ONLY a valid JSON with these fields: " +
            "style (string), qualityAttributes (string[]), " +
            "diagrams (object: diagram name -> PlantUML/Mermaid code), " +
            "documentation (markdown string), techStack (string[]), decisions (string[]). " +
            "No markdown fences, no extra text outside the JSON.";

    /** Extracts seconds from Groq's rate-limit message: "Please try again in X.XXXs" */
    private static final Pattern RETRY_SECONDS_PATTERN =
            Pattern.compile("try again in ([\\d.]+)s");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${groq.api-key}")
    private String apiKey;

    @Value("${groq.model:llama-3.1-8b-instant}")
    private String model;

    @Value("${groq.max-retries:1}")  // 1 retry: fewer = less quota burned
    private int maxRetries;

    // ── AiProvider ────────────────────────────────────────────────────────────

    @Override
    public ArchitectureOutput generate(ArchitectureInput input) {
        String userPrompt = buildUserPrompt(input);
        AiProviderException lastException = null;

        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                log.info("Calling Groq API (attempt {}/{}), model: {}", attempt, maxRetries + 1, model);
                String content = callGroq(userPrompt);
                return parseResponse(content);
            } catch (RateLimitException e) {
                // 429: wait exactly what Groq tells us + 2s buffer, then retry
                lastException = new AiProviderException("Groq rate limited: " + e.getMessage());
                if (attempt <= maxRetries) {
                    long waitMs = e.retryAfterMs + 2000L;
                    log.warn("Groq rate limited (attempt {}). Waiting {}ms before retry...", attempt, waitMs);
                    sleep(waitMs);
                } else {
                    log.error("Groq rate limited on all {} attempts. Giving up.", maxRetries + 1);
                }
            } catch (AiProviderException e) {
                lastException = e;
                log.warn("Groq attempt {} failed: {}", attempt, e.getMessage());
                if (attempt <= maxRetries) {
                    sleep(1000L * attempt);
                }
            }
        }
        throw lastException != null ? lastException
                : new AiProviderException("Groq failed after " + (maxRetries + 1) + " attempts");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String callGroq(String userPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        OpenAiRequest body = OpenAiRequest.builder()
                .model(model)
                .messages(List.of(
                        new OpenAiMessage("system", SYSTEM_PROMPT),
                        new OpenAiMessage("user", userPrompt)
                ))
                .temperature(0.3)
                .maxTokens(512)  // keep within 6,000 TPM free tier
                .build();

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    GROQ_API_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    JsonNode.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new AiProviderException("Groq returned status: " + response.getStatusCode());
            }

            String content = response.getBody()
                    .path("choices").path(0)
                    .path("message").path("content")
                    .asText("");

            if (content.isBlank()) {
                throw new AiProviderException("Groq returned empty content");
            }
            return content;

        } catch (HttpClientErrorException.TooManyRequests e) {
            // Parse the exact retry delay Groq suggests and propagate it
            long retryMs = parseRetryDelayMs(e.getResponseBodyAsString());
            log.warn("Groq 429 — suggested retry delay: {}ms", retryMs);
            throw new RateLimitException(e.getMessage(), retryMs);

        } catch (AiProviderException | RateLimitException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("Network/API error calling Groq: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts the retry delay (ms) from Groq's 429 body.
     * Falls back to 60 seconds if the pattern is not found.
     */
    private long parseRetryDelayMs(String responseBody) {
        if (responseBody != null) {
            Matcher m = RETRY_SECONDS_PATTERN.matcher(responseBody);
            if (m.find()) {
                try {
                    double seconds = Double.parseDouble(m.group(1));
                    // Ensure we wait at least 62s so the full 1-min TPM window resets
                    long parsed = (long) (seconds * 1000);
                    return Math.max(parsed, 62_000L);
                } catch (NumberFormatException ignored) { }
            }
        }
        return 62_000L; // default: wait full window reset
    }

    private ArchitectureOutput parseResponse(String rawContent) {
        String json = stripMarkdownFences(rawContent);
        try {
            JsonNode node = objectMapper.readTree(json);
            return ArchitectureOutput.builder()
                    .style(node.path("style").asText(""))
                    .qualityAttributes(parseList(node.path("qualityAttributes")))
                    .diagrams(parseMap(node.path("diagrams")))
                    .documentation(node.path("documentation").asText(""))
                    .techStack(parseList(node.path("techStack")))
                    .decisions(parseList(node.path("decisions")))
                    .build();
        } catch (Exception e) {
            throw new AiProviderException("Failed to parse Groq JSON response: " + e.getMessage(), e);
        }
    }

    private String stripMarkdownFences(String content) {
        String trimmed = content.strip();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("```[a-zA-Z]*\\n?", "");
            trimmed = trimmed.replaceAll("\\n?```$", "").strip();
        }
        return trimmed;
    }

    private String buildUserPrompt(ArchitectureInput input) {
        StringBuilder sb = new StringBuilder();
        sb.append("Domain: ").append(input.getDomain()).append("\n");
        if (input.getQualityAttributes() != null && !input.getQualityAttributes().isEmpty()) {
            sb.append("Quality attributes: ")
                    .append(String.join(", ", input.getQualityAttributes())).append("\n");
        }
        if (input.getTechStackConstraints() != null && !input.getTechStackConstraints().isEmpty()) {
            sb.append("Tech constraints: ")
                    .append(String.join(", ", input.getTechStackConstraints())).append("\n");
        }
        if (input.getNaturalLanguageDescription() != null && !input.getNaturalLanguageDescription().isBlank()) {
            sb.append("Description: ").append(input.getNaturalLanguageDescription()).append("\n");
        }
        return sb.toString();
    }

    private List<String> parseList(JsonNode node) {
        List<String> list = new ArrayList<>();
        if (node.isArray()) node.forEach(item -> list.add(item.asText()));
        return list;
    }

    private Map<String, String> parseMap(JsonNode node) {
        Map<String, String> map = new LinkedHashMap<>();
        if (node.isObject()) node.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().asText()));
        return map;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /** Internal exception carrying the rate-limit retry delay. */
    private static class RateLimitException extends RuntimeException {
        final long retryAfterMs;
        RateLimitException(String message, long retryAfterMs) {
            super(message);
            this.retryAfterMs = retryAfterMs;
        }
    }
}
