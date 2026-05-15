package com.arquisoft.genai.infrastructure.diagram;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Renders PlantUML diagram source code to PNG files and returns relative URL paths.
 *
 * Files are saved to: {diagrams-dir}/{generationId}/{sanitized-name}.png
 * Served via:         GET /api/diagrams/{generationId}/{sanitized-name}.png
 *
 * The frontend uses the URL directly as: <img src="{backendBase}{url}" />
 */
@Service
@Slf4j
public class DiagramRendererService {

    private static final String PLANTUML_START = "@startuml";
    private static final String URL_BASE = "/api/diagrams/";

    @Value("${app.diagrams-dir:./diagrams}")
    private String diagramsDir;

    /**
     * Renders each PlantUML diagram to a PNG file and returns relative URL paths.
     *
     * @param diagrams      map of diagram name → PlantUML source code
     * @param generationId  unique ID for this generation (used as folder name)
     * @return              map of diagram name → relative URL path to the PNG file
     */
    public Map<String, String> renderAndSave(Map<String, String> diagrams, String generationId) {
        Map<String, String> urls = new LinkedHashMap<>();
        if (diagrams == null || diagrams.isEmpty()) return urls;

        for (Map.Entry<String, String> entry : diagrams.entrySet()) {
            String name = entry.getKey();
            String source = entry.getValue();
            try {
                String url = renderSingle(name, source, generationId);
                urls.put(name, url);
                log.debug("Rendered diagram '{}' → {}", name, url);
            } catch (Exception e) {
                log.warn("Failed to render diagram '{}' — skipping.", name, e);
            }
        }
        return urls;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private String renderSingle(String name, String source, String generationId) throws Exception {
        String normalized = source.strip();

        if (!normalized.contains(PLANTUML_START)) {
            // Not PlantUML — wrap it so PlantUML treats it as a note diagram
            log.warn("Diagram '{}' does not contain @startuml — attempting to wrap.", name);
            normalized = "@startuml\nnote as N\n" + normalized + "\nend note\n@enduml";
        }

        String safeFilename = sanitizeFilename(name) + ".png";
        Path dir  = Paths.get(diagramsDir, generationId);
        Path file = dir.resolve(safeFilename);

        // Ensure directory exists
        Files.createDirectories(dir);

        // Render PlantUML → PNG bytes
        byte[] pngBytes = renderPlantUml(normalized);
        Files.write(file, pngBytes);

        return URL_BASE + generationId + "/" + safeFilename;
    }

    private byte[] renderPlantUml(String source) throws IOException {
        String wrapped = ensureWrapped(source);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SourceStringReader reader = new SourceStringReader(wrapped);
        reader.outputImage(out, new FileFormatOption(FileFormat.PNG));
        if (out.size() == 0) {
            throw new IOException("PlantUML rendered 0 bytes — check diagram syntax");
        }
        return out.toByteArray();
    }

    private String ensureWrapped(String source) {
        String s = source.strip();
        if (!s.startsWith("@startuml")) s = "@startuml\n" + s;
        if (!s.endsWith("@enduml"))    s = s + "\n@enduml";
        return s;
    }

    /** Converts diagram name to a safe filename: removes special chars, replaces spaces with _. */
    private String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_").replaceAll("_+", "_");
    }
}
