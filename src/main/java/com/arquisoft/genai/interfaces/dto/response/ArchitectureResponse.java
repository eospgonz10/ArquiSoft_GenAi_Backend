package com.arquisoft.genai.interfaces.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "AI-generated software architecture response")
public class ArchitectureResponse {

    @Schema(description = "Recommended architectural style", example = "Microservices")
    private String style;

    @Schema(description = "Quality attributes addressed by this architecture",
            example = "[\"security\", \"scalability\"]")
    private List<String> qualityAttributes;

    @Schema(description = "Map of diagram name to PlantUML/Mermaid code",
            example = "{\"C4-Context\": \"@startuml ...\"}")
    private Map<String, String> diagrams;

    @Schema(description = "Full architecture documentation in Markdown format")
    private String documentation;

    @Schema(description = "Recommended technology stack",
            example = "[\"Java 17\", \"Spring Boot 3\", \"PostgreSQL\"]")
    private List<String> techStack;

    @Schema(description = "Key architectural decisions with justifications")
    private List<String> decisions;
}
