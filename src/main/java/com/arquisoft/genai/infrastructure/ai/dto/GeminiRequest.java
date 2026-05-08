package com.arquisoft.genai.infrastructure.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiRequest {

    @JsonProperty("systemInstruction")
    private GeminiSystemInstruction systemInstruction;

    private List<GeminiContent> contents;

    @JsonProperty("generationConfig")
    private GeminiGenerationConfig generationConfig;

    // ── Nested DTOs ───────────────────────────────────────────────────────────

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GeminiSystemInstruction {
        private List<GeminiPart> parts;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GeminiContent {
        private String role;
        private List<GeminiPart> parts;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GeminiPart {
        private String text;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class GeminiGenerationConfig {
        private double temperature;

        @JsonProperty("maxOutputTokens")
        private int maxOutputTokens;

        /** Force Gemini to reply with valid JSON directly */
        @JsonProperty("responseMimeType")
        private String responseMimeType;
    }
}
