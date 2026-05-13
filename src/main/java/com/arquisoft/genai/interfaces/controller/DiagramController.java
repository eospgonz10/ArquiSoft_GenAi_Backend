package com.arquisoft.genai.interfaces.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Serves generated diagram PNG images publicly (no JWT required).
 *
 * Endpoint: GET /api/diagrams/{generationId}/{filename}
 * Example:  GET /api/diagrams/abc-123/C4-Context.png
 *
 * The frontend uses values from the "diagramUrls" field of the generate response
 * as the path segment: <img src="{backendBase}{diagramUrl}" />
 */
@RestController
@RequestMapping("/api/diagrams")
@Tag(name = "Diagrams", description = "Serves generated architecture diagram images")
@Slf4j
public class DiagramController {

    @Value("${app.diagrams-dir:./diagrams}")
    private String diagramsDir;

    @GetMapping("/{generationId}/{filename}")
    @Operation(
            summary = "Get a generated diagram image",
            description = "Returns a PNG image for the given generation ID and filename. No authentication required."
    )
    public ResponseEntity<Resource> getDiagram(
            @PathVariable String generationId,
            @PathVariable String filename) {

        // Security: prevent path traversal attacks
        if (generationId.contains("..") || generationId.contains("/") ||
            filename.contains("..") || filename.contains("/")) {
            return ResponseEntity.badRequest().build();
        }

        // Only serve .png files
        if (!filename.toLowerCase().endsWith(".png")) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Path filePath = Paths.get(diagramsDir).resolve(generationId).resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                log.warn("Diagram not found: {}/{}", generationId, filename);
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(resource);

        } catch (MalformedURLException e) {
            log.error("Invalid diagram path: {}/{}", generationId, filename);
            return ResponseEntity.badRequest().build();
        }
    }
}
