package com.arquisoft.genai.application.usecase;

import com.arquisoft.genai.application.model.ArchitectureInput;
import com.arquisoft.genai.application.model.ArchitectureOutput;
import com.arquisoft.genai.application.port.AiProvider;
import com.arquisoft.genai.application.validation.ArchitectureResponseValidator;
import com.arquisoft.genai.infrastructure.diagram.DiagramRendererService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenerateArchitectureUseCase {

    private final AiProvider aiProvider;
    private final ArchitectureResponseValidator validator;
    private final DiagramRendererService diagramRenderer;

    public ArchitectureOutput generate(ArchitectureInput input) {
        log.info("Generating architecture for domain: '{}'", input.getDomain());

        // 1. Call AI provider — single attempt, errors propagate as AiProviderException
        ArchitectureOutput output = aiProvider.generate(input);
        ArchitectureOutput validated = validator.validate(output);

        // 2. Render PlantUML diagrams to PNG files and get their URLs
        String generationId = UUID.randomUUID().toString();
        Map<String, String> diagramUrls = diagramRenderer.renderAndSave(
                validated.getDiagrams(), generationId);
        log.info("Rendered {}/{} diagrams for domain '{}' (id: {})",
                diagramUrls.size(), validated.getDiagrams().size(), input.getDomain(), generationId);

        // 3. Return enriched output
        return ArchitectureOutput.builder()
                .style(validated.getStyle())
                .qualityAttributes(validated.getQualityAttributes())
                .diagrams(validated.getDiagrams())
                .diagramUrls(diagramUrls)
                .documentation(validated.getDocumentation())
                .techStack(validated.getTechStack())
                .decisions(validated.getDecisions())
                .build();
    }
}
