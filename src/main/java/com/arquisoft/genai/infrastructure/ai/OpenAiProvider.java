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
public class OpenAiProvider extends AiProviderSupport implements AiProvider {

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

        private static final String SYSTEM_PROMPT =
            "You are a senior software architect specialized in UML and C4. Given the domain, the provided " +
            "documentation, quality attributes, and tech constraints, return ONLY a valid JSON with these fields: " +
            "style (string), qualityAttributes (string[]), diagrams (object: diagram name -> PlantUML code using " +
            "@startuml/@enduml syntax ONLY), documentation (markdown string), techStack (string[]), decisions (string[]). " +
            "The diagrams object must include at least: C4-Context, C4-Container, C4-Component, Use-Case, " +
            "Component-Diagram, Class-Diagram, and Sequence-Primary-Flow. " +
            "Use C4-PlantUML includes for C4 diagrams and standard PlantUML syntax for UML diagrams. " +
            "Make the diagrams specific, realistic, and mutually consistent. No markdown fences, no extra text outside the JSON. " +
            "Follow the compact few-shot example exactly (use the same JSON shape and PlantUML snippets).\n\n" +
            "Example Input:\n" +
            "Domain: Payments\n" +
            "Quality attributes: security, scalability\n" +
            "Tech constraints: Java, PostgreSQL\n" +
            "Description: Payment processing system handling card transactions.\n\n" +
            "Example Output (compact):\n" +
            "{\n" +
            "  \"style\": \"Microservices\",\n" +
            "  \"qualityAttributes\": [\"security\", \"scalability\"],\n" +
            "  \"diagrams\": {\n" +
            "    \"C4-Context\": \"@startuml\\n!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Context.puml\\nPerson(user, \\\"End User\\\")\\nSystem(payments, \\\"Payments System\\\")\\nRel(user,payments, \\\"Uses\\\")\\n@enduml\",\n" +
            "    \"C4-Container\": \"@startuml\\n!include https://raw.githubusercontent.com/plantuml-stdlib/C4-PlantUML/master/C4_Container.puml\\nSystem_Boundary(sb, \\\"Payments System\\\") { Container(api, \\\"API\\\", \\\"Spring Boot\\\") }\\n@enduml\",\n" +
            "    \"Class-Diagram\": \"@startuml\\nclass Payment { +id: UUID }\\n@enduml\"\n" +
            "  },\n" +
            "  \"documentation\": \"# Payments...\",\n" +
            "  \"techStack\": [\"Java\", \"PostgreSQL\"],\n" +
            "  \"decisions\": [\"Use JWT\", \"Use service bus\"]\n" +
            "}\n\n" +
            "Ensure all JSON string values are properly escaped and PlantUML snippets are valid @startuml/.../@enduml blocks. " +
            "Do not include any explanatory text outside the JSON.";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    @Value("${openai.max-retries:2}")
    private int maxRetries;

    // ── AiProvider ────────────────────────────────────────────────────────────

    /**
     * Single attempt — no retries. Errors propagate immediately to the caller.
     * The timeout is controlled by RestTemplateConfig (AI_READ_TIMEOUT_SECONDS).
     */
    @Override
    public ArchitectureOutput generate(ArchitectureInput input) {
        return executeWithRetry("OpenAI", maxRetries, () -> {
            log.info("Calling OpenAI API, model: {}", model);
            String content = callOpenAi(buildUserPrompt(input));
            return parseResponse(content);
        });
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

        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 429) {
                throw buildRateLimitException("OpenAI", e);
            }
            if (e.getStatusCode().is5xxServerError()) {
                throw new AiTransientException(
                        "OpenAI temporary HTTP error " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
            }
            throw new AiProviderException(
                    "OpenAI HTTP error " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);

        } catch (ResourceAccessException e) {
            throw buildTransientException("OpenAI", e.getMessage(), e);

        } catch (AiProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new AiProviderException("Network/API error calling OpenAI: " + e.getMessage(), e);
        }
    }

    private ArchitectureOutput parseResponse(String rawContent) {
        return parseStandardResponse(objectMapper, rawContent, "OpenAI");
    }

    @Override
    protected ObjectMapper objectMapper() {
        return objectMapper;
    }
}
