package com.arquisoft.genai.application.validation;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for sanitizing and validating AI-generated diagram code.
 * Supports PlantUML and Mermaid formats.
 */
@Slf4j
public final class DiagramSanitizer {

    private DiagramSanitizer() {
        // Utility class
    }

    public static String sanitize(String diagramCode) {
        if (diagramCode == null || diagramCode.isBlank()) {
            return "@startuml\nnote \"Diagram not available\" as N1\n@enduml";
        }

        String trimmed = diagramCode.strip();

        if (isValidPlantUml(trimmed)) {
            return ensurePlantUmlClosing(trimmed);
        }
        if (isValidMermaid(trimmed)) {
            return trimmed;
        }

        // Strip markdown code fences if present
        if (trimmed.startsWith("```")) {
            String stripped = trimmed.replaceFirst("```[a-zA-Z]*\\n?", "").replaceAll("\n?```$", "").strip();
            if (isValidPlantUml(stripped)) return ensurePlantUmlClosing(stripped);
            if (isValidMermaid(stripped)) return stripped;
        }

        log.warn("Unknown diagram format detected, wrapping in PlantUML note");
        return "@startuml\nnote \"" + trimmed.replace("\"", "'") + "\" as N1\n@enduml";
    }

    public static boolean isValidPlantUml(String code) {
        return code != null && code.contains("@startuml");
    }

    public static boolean isValidMermaid(String code) {
        if (code == null) return false;
        String t = code.strip();
        return t.startsWith("graph ") || t.startsWith("sequenceDiagram")
                || t.startsWith("classDiagram") || t.startsWith("flowchart")
                || t.startsWith("erDiagram") || t.startsWith("stateDiagram")
                || t.startsWith("gantt") || t.startsWith("pie");
    }

    private static String ensurePlantUmlClosing(String code) {
        if (!code.contains("@enduml")) {
            log.warn("PlantUML diagram missing @enduml, appending it");
            return code + "\n@enduml";
        }
        return code;
    }
}
