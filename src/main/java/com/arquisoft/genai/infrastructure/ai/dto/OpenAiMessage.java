package com.arquisoft.genai.infrastructure.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OpenAiMessage {
    private String role;
    private String content;
}
