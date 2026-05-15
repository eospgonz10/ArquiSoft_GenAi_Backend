package com.arquisoft.genai.infrastructure.ai;

import com.arquisoft.genai.application.exception.AiProviderException;
import com.arquisoft.genai.application.exception.AiTransientException;
import com.arquisoft.genai.application.model.ArchitectureInput;
import com.arquisoft.genai.application.model.ArchitectureOutput;
import com.arquisoft.genai.application.port.AiProvider;
import com.arquisoft.genai.infrastructure.ai.dto.OpenAiMessage;
import com.arquisoft.genai.infrastructure.ai.dto.OpenAiRequest;
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
public class GroqProvider extends AiProviderSupport implements AiProvider {

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";

    private static final String SYSTEM_PROMPT =
            "You are a senior software architect specialized in UML and C4. Return ONLY a valid JSON object with " +
            "these fields: style (string), qualityAttributes (string[]), diagrams (object: diagram name -> PlantUML code " +
            "using @startuml/@enduml syntax ONLY), documentation (markdown string), techStack (string[]), decisions (string[]). " +
            "The diagrams object must include at least: C4-Context, C4-Container, C4-Component, Use-Case, Component-Diagram, " +
            "Class-Diagram, and Sequence-Primary-Flow. Use C4-PlantUML includes for C4 diagrams and standard PlantUML syntax " +
            "for UML diagrams. Make the diagrams specific, realistic, and mutually consistent. Follow the compact few-shot example below.\n\n" +
            "Example Input:\nDomain: Payments\nQuality attributes: security, scalability\nTech constraints: Java, PostgreSQL\nDescription: Payment processing system handling card transactions.\n\n" +
            "Example Output (compact):\n{\n  \"style\": \"Microservices\",\n  \"qualityAttributes\": [\"security\", \"scalability\"],\n  \"diagrams\": {\n    \"C4-Context\": \"@startuml\\n!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Context.puml\\nPerson(user, \\\"End User\\\")\\nSystem(payments, \\\"Payments System\\\")\\nRel(user,payments, \\\"Uses\\\")\\n@enduml\",\n    \"C4-Container\": \"@startuml\\n!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Container.puml\\nSystem_Boundary(sb, \\\"Payments System\\\") { Container(api, \\\"API\\\", \\\"Spring Boot\\\") }\\n@enduml\",\n    \"Class-Diagram\": \"@startuml\\nclass Payment { +id: UUID }\\n@enduml\"\n  },\n  \"documentation\": \"# Payments...\",\n  \"techStack\": [\"Java\", \"PostgreSQL\"],\n  \"decisions\": [\"Use JWT\", \"Use service bus\"]\n}\n\nEnsure JSON strings are escaped and PlantUML snippets are valid. Output ONLY the JSON object, no markdown fences, no extra text.";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${groq.api-key}")
    private String apiKey;

    @Value("${groq.model:llama-3.1-8b-instant}")
    private String model;

    @Value("${groq.max-retries:2}")
    private int maxRetries;

    // ── AiProvider ────────────────────────────────────────────────────────────

    /**
     * Single attempt — no retries. Errors propagate immediately to the caller.
     */
    @Override
    public ArchitectureOutput generate(ArchitectureInput input) {
        return executeWithRetry("Groq", maxRetries, () -> {
            log.info("Calling Groq API, model: {}", model);
            String content = callGroq(buildUserPrompt(input));
            return parseResponse(content);
        });
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

        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 429) {
                throw buildRateLimitException("Groq", e);
            }
            if (e.getStatusCode().is5xxServerError()) {
                throw new AiTransientException(
                        "Groq temporary HTTP error " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
            }
            throw new AiProviderException(
                    "Groq HTTP error " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);

        } catch (ResourceAccessException e) {
            throw buildTransientException("Groq", e.getMessage(), e);

        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("Network/API error calling Groq: " + e.getMessage(), e);
        }
    }

    private ArchitectureOutput parseResponse(String rawContent) {
        return parseStandardResponse(objectMapper, rawContent, "Groq");
    }

    @Override
    protected ObjectMapper objectMapper() {
        return objectMapper;
    }
}
