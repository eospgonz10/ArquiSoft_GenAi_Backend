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

    @Schema(description = "Map of diagram name to PlantUML source code",
            example = "{\"C4-Context\": \"@startuml ...\"}")
    private Map<String, String> diagrams;

    @Schema(description = """
            Map of diagram name to a relative image URL.
            Use as: <img src="{backendBaseUrl}{url}" />
            Example value: "/api/diagrams/abc123/C4-Context.png"
            The images are served publicly (no JWT required).
            """,
            example = "{\"C4-Context\": \"/api/diagrams/abc123/C4-Context.png\"}")
    private Map<String, String> diagramUrls;

    @Schema(description = "Full architecture documentation in Markdown format")
    private String documentation;

    @Schema(description = "Recommended technology stack",
            example = "[\"Java 17\", \"Spring Boot 3\", \"PostgreSQL\"]")
    private List<String> techStack;

        @Schema(description = "Key architectural decisions with justifications")
        private List<String> decisions;

        /** Identifier used to group rendered diagrams for a single generation. */
        @Schema(description = "Unique identifier for the generation of diagrams", example = "gen-12345")
        private String generationId;
}
