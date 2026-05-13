package com.arquisoft.genai.infrastructure.ai;

import com.arquisoft.genai.application.exception.AiProviderException;
import com.arquisoft.genai.application.model.ArchitectureInput;
import com.arquisoft.genai.application.model.ArchitectureOutput;
import com.arquisoft.genai.application.port.AiProvider;
import com.arquisoft.genai.infrastructure.ai.dto.GeminiRequest;
import com.arquisoft.genai.infrastructure.ai.dto.GeminiRequest.*;
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
 * Google Gemini provider.
 * Activated when ai.provider=gemini.
 *
 * Note: Free tier may be blocked for new accounts created in 2026.
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini")
@RequiredArgsConstructor
@Slf4j
public class GeminiProvider implements AiProvider {

    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}";

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

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String model;

    // ── AiProvider ────────────────────────────────────────────────────────────

    /**
     * Single attempt — no retries. Errors propagate immediately to the caller.
     */
    @Override
    public ArchitectureOutput generate(ArchitectureInput input) {
        log.info("Calling Gemini API, model: {}", model);
        String content = callGemini(buildUserPrompt(input));
        return parseResponse(content);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String callGemini(String userPrompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        GeminiRequest body = GeminiRequest.builder()
                .systemInstruction(new GeminiSystemInstruction(
                        List.of(new GeminiPart(SYSTEM_PROMPT))))
                .contents(List.of(new GeminiContent(
                        "user",
                        List.of(new GeminiPart(userPrompt)))))
                .generationConfig(GeminiGenerationConfig.builder()
                        .temperature(0.3)
                        .maxOutputTokens(1024)
                        .responseMimeType("application/json")
                        .build())
                .build();

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    GEMINI_API_URL, HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    JsonNode.class,
                    model, apiKey);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new AiProviderException("Gemini returned status: " + response.getStatusCode());
            }

            String content = response.getBody()
                    .path("candidates").path(0)
                    .path("content").path("parts").path(0)
                    .path("text").asText("");

            if (content.isBlank()) {
                throw new AiProviderException("Gemini returned empty content");
            }
            return content;

        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new AiProviderException(
                "Gemini rate limit exceeded (429). Free tier may be blocked for new accounts. " +
                "Consider switching AI_PROVIDER=groq. Error: " + e.getResponseBodyAsString(), e);

        } catch (HttpClientErrorException e) {
            throw new AiProviderException(
                "Gemini HTTP error " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);

        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("Network/API error calling Gemini: " + e.getMessage(), e);
        }
    }

    private ArchitectureOutput parseResponse(String rawContent) {
        try {
            JsonNode node = objectMapper.readTree(rawContent);
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
            throw new AiProviderException("Failed to parse Gemini JSON response: " + e.getMessage(), e);
        }
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
