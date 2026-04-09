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
    Map<String, String> diagrams;
    String documentation;
    List<String> techStack;
    List<String> decisions;
}
