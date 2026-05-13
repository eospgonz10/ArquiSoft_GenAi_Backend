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
 * OpenAI GPT-4o-mini provider.
 * Activated when ai.provider=openai (default).
 *
 * IMPORTANT: gpt-4o-mini requires a paid OpenAI account (Tier 1+, min $5 credit).
 * The free tier does NOT support this model.
 * To use a free alternative, switch AI_PROVIDER=groq in .env.
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class OpenAiProvider implements AiProvider {

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    private static final String SYSTEM_PROMPT =
            "You are a software architect. Given domain, quality attributes and tech constraints, " +
            "return ONLY a valid JSON with these fields: " +
            "style (string), qualityAttributes (string[]), " +
            "diagrams (object: diagram name -> PlantUML code using @startuml/@enduml syntax ONLY), " +
            "documentation (markdown string), techStack (string[]), decisions (string[]). " +
            "IMPORTANT: diagrams must use PlantUML syntax starting with @startuml. " +
            "No markdown fences, no extra text outside the JSON.";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    // ── AiProvider ────────────────────────────────────────────────────────────

    /**
     * Single attempt — no retries. Errors propagate immediately to the caller.
     * The timeout is controlled by RestTemplateConfig (AI_READ_TIMEOUT_SECONDS).
     */
    @Override
    public ArchitectureOutput generate(ArchitectureInput input) {
        log.info("Calling OpenAI API, model: {}", model);
        String content = callOpenAi(buildUserPrompt(input));
        return parseResponse(content);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String callOpenAi(String userPrompt) {
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
                .build();

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    OPENAI_API_URL, HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    JsonNode.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new AiProviderException("OpenAI returned status: " + response.getStatusCode());
            }

            String content = response.getBody()
                    .path("choices").path(0)
                    .path("message").path("content")
                    .asText("");

            if (content.isBlank()) {
                throw new AiProviderException("OpenAI returned empty content");
            }
            return content;

        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new AiProviderException(
                "OpenAI rate limit exceeded (429). " +
                "If on free tier, gpt-4o-mini is not available without payment. " +
                "Consider switching AI_PROVIDER=groq for a free alternative. " +
                "Error: " + e.getResponseBodyAsString(), e);

        } catch (HttpClientErrorException e) {
            throw new AiProviderException(
                "OpenAI HTTP error " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);

        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("Network/API error calling OpenAI: " + e.getMessage(), e);
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
            throw new AiProviderException("Failed to parse OpenAI JSON response: " + e.getMessage(), e);
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
