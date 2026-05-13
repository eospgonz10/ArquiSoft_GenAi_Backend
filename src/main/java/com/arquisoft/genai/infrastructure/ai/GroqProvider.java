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

/**
 * Groq AI provider (OpenAI-compatible API).
 * Activated when ai.provider=groq.
 *
 * Free tier: 14,400 req/day, 6,000 TPM — no credit card required.
 * Best free alternative when OpenAI has no credits.
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "groq")
@RequiredArgsConstructor
@Slf4j
public class GroqProvider implements AiProvider {

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    private static final String SYSTEM_PROMPT =
            "You are a software architect. Return ONLY a valid JSON object with these fields: " +
            "style (string), qualityAttributes (string[]), " +
            "diagrams (object: diagram name -> PlantUML code using @startuml/@enduml syntax ONLY), " +
            "documentation (markdown string), techStack (string[]), decisions (string[]). " +
            "IMPORTANT: All JSON string values must be properly escaped. " +
            "Diagrams must use PlantUML syntax starting with @startuml. " +
            "Output ONLY the JSON object, no markdown fences, no extra text.";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${groq.api-key}")
    private String apiKey;

    @Value("${groq.model:llama-3.1-8b-instant}")
    private String model;

    // ── AiProvider ────────────────────────────────────────────────────────────

    /**
     * Single attempt — no retries. Errors propagate immediately to the caller.
     */
    @Override
    public ArchitectureOutput generate(ArchitectureInput input) {
        log.info("Calling Groq API, model: {}", model);
        String content = callGroq(buildUserPrompt(input));
        return parseResponse(content);
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
                .maxTokens(1024)
                .responseFormat(Map.of("type", "json_object"))  // Forces valid JSON output
                .build();

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    GROQ_API_URL, HttpMethod.POST,
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
            throw new AiProviderException(
                "Groq rate limit exceeded (429). Free tier: 6,000 TPM / 30 RPM. " +
                "Wait 1 minute and retry. Error: " + e.getResponseBodyAsString(), e);

        } catch (HttpClientErrorException e) {
            throw new AiProviderException(
                "Groq HTTP error " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);

        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("Network/API error calling Groq: " + e.getMessage(), e);
        }
    }

    private ArchitectureOutput parseResponse(String rawContent) {
        String json = stripMarkdownFences(rawContent);
        try {
            JsonNode node = objectMapper.readTree(json);
            return ArchitectureOutput.builder()
                    .style(node.path("style").asText(""))
                    .qualityAttributes(parseList(node.path("qualityAttributes")))
                    .diagrams(parseMap(node.path("diagrams")))
                    .diagramUrls(null)  // populated by GenerateArchitectureUseCase
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
}
