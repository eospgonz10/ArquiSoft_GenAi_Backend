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
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * OpenAI GPT-4o-mini provider implementation.
 * Activated when ai.provider=openai (default).
 */
@Component
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class OpenAiProvider implements AiProvider {

    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";

    private static final String SYSTEM_PROMPT = """
            Eres un arquitecto de software experto. Dado el dominio, atributos de calidad y restricciones técnicas,
            genera una arquitectura de software y su documentación.
            Devuelve EXCLUSIVAMENTE un JSON válido con los siguientes campos:
            - style (string): nombre del estilo arquitectónico
            - qualityAttributes (array de strings): atributos de calidad abordados
            - diagrams (objeto): claves son nombres de diagramas, valores son código PlantUML o Mermaid válido
            - documentation (string en Markdown): descripción detallada de la arquitectura
            - techStack (array de strings): tecnologías recomendadas
            - decisions (array de strings): decisiones arquitectónicas clave con justificación
            NO incluyas ningún texto, explicación ni bloque de código markdown fuera del JSON.
            """;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    @Value("${openai.max-retries:2}")
    private int maxRetries;

    @Override
    public ArchitectureOutput generate(ArchitectureInput input) {
        String userPrompt = buildUserPrompt(input);
        AiProviderException lastException = null;

        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                log.info("Calling OpenAI API (attempt {}/{}), model: {}", attempt, maxRetries + 1, model);
                String content = callOpenAi(userPrompt);
                return parseResponse(content);
            } catch (AiProviderException e) {
                lastException = e;
                log.warn("OpenAI attempt {} failed: {}", attempt, e.getMessage());
                if (attempt <= maxRetries) {
                    sleep(1000L * attempt); // simple backoff
                }
            }
        }
        throw lastException != null ? lastException
                : new AiProviderException("Failed after " + (maxRetries + 1) + " attempts");
    }

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
                .maxTokens(4096)
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
            trimmed = trimmed.replaceAll("\n?```$", "").strip();
        }
        return trimmed;
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

    private String buildUserPrompt(ArchitectureInput input) {
        StringBuilder sb = new StringBuilder();
        sb.append("Dominio: ").append(input.getDomain()).append("\n");
        if (input.getQualityAttributes() != null && !input.getQualityAttributes().isEmpty()) {
            sb.append("Atributos de calidad requeridos: ")
                    .append(String.join(", ", input.getQualityAttributes())).append("\n");
        }
        if (input.getTechStackConstraints() != null && !input.getTechStackConstraints().isEmpty()) {
            sb.append("Restricciones tecnológicas: ")
                    .append(String.join(", ", input.getTechStackConstraints())).append("\n");
        }
        if (input.getNaturalLanguageDescription() != null && !input.getNaturalLanguageDescription().isBlank()) {
            sb.append("Descripción adicional: ").append(input.getNaturalLanguageDescription()).append("\n");
        }
        return sb.toString();
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
