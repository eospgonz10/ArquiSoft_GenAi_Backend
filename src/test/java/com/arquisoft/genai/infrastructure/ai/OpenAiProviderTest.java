package com.arquisoft.genai.infrastructure.ai;

import com.arquisoft.genai.application.exception.AiProviderException;
import com.arquisoft.genai.application.model.ArchitectureInput;
import com.arquisoft.genai.application.model.ArchitectureOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OpenAiProvider Tests")
class OpenAiProviderTest {

    @Mock private RestTemplate restTemplate;

    private OpenAiProvider provider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        provider = new OpenAiProvider(restTemplate, objectMapper);
        ReflectionTestUtils.setField(provider, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(provider, "model", "gpt-4o-mini");
        ReflectionTestUtils.setField(provider, "maxRetries", 0);
    }

    private ArchitectureInput buildInput() {
        return ArchitectureInput.builder()
                .domain("fintech")
                .qualityAttributes(List.of("security", "scalability"))
                .techStackConstraints(List.of("Java"))
                .naturalLanguageDescription("A payment platform")
                .build();
    }

    /**
     * Builds a mock OpenAI API JsonNode response where the content field
     * contains the architecture JSON string. Uses ObjectMapper to avoid
     * manual escaping of special characters like newlines inside JSON strings.
     */
    private com.fasterxml.jackson.databind.JsonNode buildOpenAiResponse(String contentJson) throws Exception {
        // The architecture JSON becomes the string value of the "content" field.
        // objectMapper.writeValueAsString() produces a properly escaped JSON string literal.
        String openAiEnvelope = String.format(
                "{\"choices\":[{\"message\":{\"content\":%s}}]}",
                objectMapper.writeValueAsString(contentJson)
        );
        return objectMapper.readTree(openAiEnvelope);
    }

    private String buildValidArchitectureJson() {
        return """
                {
                  "style": "Microservices",
                  "qualityAttributes": ["security", "scalability"],
                  "diagrams": {"C4-Context": "@startuml\\n@enduml"},
                  "documentation": "# Fintech Architecture",
                  "techStack": ["Java 17", "Spring Boot"],
                  "decisions": ["Use JWT for auth"]
                }
                """;
    }

    @Test
    @DisplayName("generate: parses valid OpenAI JSON response correctly")
    void generate_validResponse_parsesCorrectly() throws Exception {
        com.fasterxml.jackson.databind.JsonNode mockNode =
                buildOpenAiResponse(buildValidArchitectureJson());

        ResponseEntity<com.fasterxml.jackson.databind.JsonNode> mockResponse =
                ResponseEntity.ok(mockNode);

        given(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .willReturn(mockResponse);

        ArchitectureOutput output = provider.generate(buildInput());

        assertThat(output.getStyle()).isEqualTo("Microservices");
        assertThat(output.getQualityAttributes()).contains("security");
        assertThat(output.getTechStack()).contains("Java 17");
        assertThat(output.getDiagrams()).containsKey("C4-Context");
        assertThat(output.getDecisions()).contains("Use JWT for auth");
    }

    @Test
    @DisplayName("generate: strips markdown fences from response content")
    void generate_stripsMarkdownFences() throws Exception {
        String contentWithFences = "```json\n" + buildValidArchitectureJson() + "\n```";
        com.fasterxml.jackson.databind.JsonNode mockNode =
                buildOpenAiResponse(contentWithFences);

        ResponseEntity<com.fasterxml.jackson.databind.JsonNode> mockResponse =
                ResponseEntity.ok(mockNode);

        given(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .willReturn(mockResponse);

        ArchitectureOutput output = provider.generate(buildInput());

        assertThat(output.getStyle()).isEqualTo("Microservices");
    }

    @Test
    @DisplayName("generate: throws AiProviderException on network error")
    void generate_networkError_throwsException() {
        given(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
                eq(com.fasterxml.jackson.databind.JsonNode.class)))
                .willThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> provider.generate(buildInput()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("Network/API error");
    }
}
