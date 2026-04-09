package com.arquisoft.genai.application.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Application-layer value object for architecture generation input.
 * Kept separate from the interfaces DTO to maintain Clean Architecture boundaries.
 */
@Value
@Builder
public class ArchitectureInput {
    String domain;
    List<String> qualityAttributes;
    List<String> techStackConstraints;
    String naturalLanguageDescription;
}
