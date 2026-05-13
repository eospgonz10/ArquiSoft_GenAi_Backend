package com.arquisoft.genai.infrastructure.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)  // omit null fields (e.g. responseFormat if not set)
public class OpenAiRequest {
    private String model;
    private List<OpenAiMessage> messages;
    private double temperature;

    @JsonProperty("max_tokens")
    private int maxTokens;

    /**
     * Forces the model to always return valid JSON.
     * Set to: {"type": "json_object"}
     *
     * Supported by OpenAI (gpt-3.5-turbo+, gpt-4o-mini) and Groq (most models).
     * When enabled, the model MUST include the word "json" in the system prompt.
     */
    @JsonProperty("response_format")
    private Map<String, String> responseFormat;
}
