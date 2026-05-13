package com.arquisoft.genai.application.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Application-layer value object for the AI-generated architecture output.
 */
@Value
@Builder
public class ArchitectureOutput {
    String style;
    List<String> qualityAttributes;
    /** Diagram source code (PlantUML) keyed by diagram name. */
    Map<String, String> diagrams;
    /**
     * Rendered diagram URLs keyed by diagram name.
     * Values are relative paths like "/api/diagrams/{generationId}/{name}.png"
     * that the frontend can use directly as <img src="..."> after prepending the backend base URL.
     * Populated by GenerateArchitectureUseCase after AI generation.
     */
    Map<String, String> diagramUrls;
    String documentation;
    List<String> techStack;
    List<String> decisions;
}
