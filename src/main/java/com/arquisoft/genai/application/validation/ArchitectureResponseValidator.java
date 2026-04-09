package com.arquisoft.genai.application.validation;

import com.arquisoft.genai.application.model.ArchitectureOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage 5: Validates and repairs the AI-generated architecture output.
 * Attempts field-level repair before throwing an error.
 */
@Component
@Slf4j
public class ArchitectureResponseValidator {

    public ArchitectureOutput validate(ArchitectureOutput output) {
        List<String> missing = detectMissingFields(output);
        if (!missing.isEmpty()) {
            log.warn("AI response has missing fields: {}. Attempting repair.", missing);
            output = repair(output);
        }
        return sanitizeDiagrams(output);
    }

    private List<String> detectMissingFields(ArchitectureOutput o) {
        List<String> missing = new ArrayList<>();
        if (o.getStyle() == null || o.getStyle().isBlank()) missing.add("style");
        if (o.getQualityAttributes() == null || o.getQualityAttributes().isEmpty()) missing.add("qualityAttributes");
        if (o.getDiagrams() == null || o.getDiagrams().isEmpty()) missing.add("diagrams");
        if (o.getDocumentation() == null || o.getDocumentation().isBlank()) missing.add("documentation");
        if (o.getTechStack() == null || o.getTechStack().isEmpty()) missing.add("techStack");
        if (o.getDecisions() == null || o.getDecisions().isEmpty()) missing.add("decisions");
        return missing;
    }

    private ArchitectureOutput repair(ArchitectureOutput o) {
        return ArchitectureOutput.builder()
                .style(nonBlank(o.getStyle(), "Not specified"))
                .qualityAttributes(nonEmpty(o.getQualityAttributes(), List.of("Not specified")))
                .diagrams(nonEmpty(o.getDiagrams(), Map.of()))
                .documentation(nonBlank(o.getDocumentation(), "*Documentation not available from AI response.*"))
                .techStack(nonEmpty(o.getTechStack(), List.of("Not specified")))
                .decisions(nonEmpty(o.getDecisions(), List.of("No architectural decisions provided")))
                .build();
    }

    private ArchitectureOutput sanitizeDiagrams(ArchitectureOutput o) {
        if (o.getDiagrams() == null || o.getDiagrams().isEmpty()) return o;
        Map<String, String> sanitized = new HashMap<>();
        o.getDiagrams().forEach((k, v) -> sanitized.put(k, DiagramSanitizer.sanitize(v)));
        return ArchitectureOutput.builder()
                .style(o.getStyle())
                .qualityAttributes(o.getQualityAttributes())
                .diagrams(sanitized)
                .documentation(o.getDocumentation())
                .techStack(o.getTechStack())
                .decisions(o.getDecisions())
                .build();
    }

    private String nonBlank(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private <T> List<T> nonEmpty(List<T> list, List<T> fallback) {
        return (list != null && !list.isEmpty()) ? list : fallback;
    }

    private <K, V> Map<K, V> nonEmpty(Map<K, V> map, Map<K, V> fallback) {
        return (map != null && !map.isEmpty()) ? map : fallback;
    }
}
