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
public class OpenAiRequest {
    private String model;
    private List<OpenAiMessage> messages;
    private double temperature;
    @JsonProperty("max_tokens")
    private int maxTokens;
}
