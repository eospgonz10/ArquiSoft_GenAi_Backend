package com.arquisoft.genai.interfaces.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Architecture generation request")
public class ArchitectureGenerationRequest {

    @NotBlank(message = "Domain is required")
    @Schema(description = "Business domain of the application",
            example = "fintech", requiredMode = Schema.RequiredMode.REQUIRED)
    private String domain;

    @Schema(description = "Desired quality attributes",
            example = "[\"security\", \"scalability\", \"performance\"]")
    private List<String> qualityAttributes;

    @Schema(description = "Technology constraints or preferences",
            example = "[\"Java\", \"PostgreSQL\", \"Docker\"]")
    private List<String> techStackConstraints;

    @Schema(description = "Optional free-text description of the system",
            example = "A payment processing platform handling 10k TPS with PCI-DSS compliance")
    private String naturalLanguageDescription;
}
