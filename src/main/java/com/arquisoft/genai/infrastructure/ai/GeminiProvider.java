package com.arquisoft.genai.infrastructure.ai;

import com.arquisoft.genai.application.exception.AiProviderException;
import com.arquisoft.genai.application.exception.AiTransientException;
import com.arquisoft.genai.application.model.ArchitectureInput;
import com.arquisoft.genai.application.model.ArchitectureOutput;
import com.arquisoft.genai.application.port.AiProvider;
import com.arquisoft.genai.infrastructure.ai.dto.GeminiRequest;
import com.arquisoft.genai.infrastructure.ai.dto.GeminiRequest.*;
import com.arquisoft.genai.infrastructure.ai.support.AiProviderSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
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
public class GeminiProvider extends AiProviderSupport implements AiProvider {

    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={apiKey}";

    private static final String SYSTEM_PROMPT =
            "You are a senior software architect specialized in UML and C4. Given the domain, the provided " +
            "documentation, quality attributes, and tech constraints, return ONLY a valid JSON with these fields: " +
            "style (string), qualityAttributes (string[]), diagrams (object: diagram name -> PlantUML code using " +
            "@startuml/@enduml syntax ONLY), documentation (markdown string), techStack (string[]), decisions (string[]). " +
            "The diagrams object must include at least: C4-Context, C4-Container, C4-Component, Use-Case, Component-Diagram, " +
            "Class-Diagram, and Sequence-Primary-Flow. Use C4-PlantUML includes for C4 diagrams and standard PlantUML syntax " +
            "for UML diagrams. Make the diagrams specific, realistic, and mutually consistent. Follow the compact few-shot example below.\n\n" +
            "Example Input:\nDomain: Payments\nQuality attributes: security, scalability\nTech constraints: Java, PostgreSQL\nDescription: Payment processing system handling card transactions.\n\n" +
            "Example Output (compact):\n{\n  \"style\": \"Microservices\",\n  \"qualityAttributes\": [\"security\", \"scalability\"],\n  \"diagrams\": {\n    \"C4-Context\": \"@startuml\\n!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Context.puml\\nPerson(user, \\\"End User\\\")\\nSystem(payments, \\\"Payments System\\\")\\nRel(user,payments, \\\"Uses\\\")\\n@enduml\",\n    \"C4-Container\": \"@startuml\\n!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Container.puml\\nSystem_Boundary(sb, \\\"Payments System\\\") { Container(api, \\\"API\\\", \\\"Spring Boot\\\") }\\n@enduml\",\n    \"Class-Diagram\": \"@startuml\\nclass Payment { +id: UUID }\\n@enduml\"\n  },\n  \"documentation\": \"# Payments...\",\n  \"techStack\": [\"Java\", \"PostgreSQL\"],\n  \"decisions\": [\"Use JWT\", \"Use service bus\"]\n}\n\nEnsure JSON strings are escaped and PlantUML snippets are valid. No markdown fences, no extra text outside the JSON.";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api-key}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String model;

    @Value("${gemini.max-retries:2}")
    private int maxRetries;

    // ── AiProvider ────────────────────────────────────────────────────────────

    /**
     * Single attempt — no retries. Errors propagate immediately to the caller.
     */
    @Override
    public ArchitectureOutput generate(ArchitectureInput input) {
        return executeWithRetry("Gemini", maxRetries, () -> {
            log.info("Calling Gemini API, model: {}", model);
            String content = callGemini(buildUserPrompt(input));
            return parseResponse(content);
        });
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

        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 429) {
                throw buildRateLimitException("Gemini", e);
            }
            if (e.getStatusCode().is5xxServerError()) {
                throw new AiTransientException(
                        "Gemini temporary HTTP error " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
            }
            throw new AiProviderException(
                    "Gemini HTTP error " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);

        } catch (ResourceAccessException e) {
            throw buildTransientException("Gemini", e.getMessage(), e);

        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("Network/API error calling Gemini: " + e.getMessage(), e);
        }
    }

    private ArchitectureOutput parseResponse(String rawContent) {
        return parseStandardResponse(objectMapper, rawContent, "Gemini");
    }

    @Override
    protected ObjectMapper objectMapper() {
        return objectMapper;
    }
}
