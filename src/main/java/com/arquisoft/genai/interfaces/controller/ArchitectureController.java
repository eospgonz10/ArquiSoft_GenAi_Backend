package com.arquisoft.genai.interfaces.controller;

import com.arquisoft.genai.application.model.ArchitectureInput;
import com.arquisoft.genai.application.model.ArchitectureOutput;
import com.arquisoft.genai.application.usecase.GenerateArchitectureUseCase;
import com.arquisoft.genai.interfaces.dto.request.ArchitectureGenerationRequest;
import com.arquisoft.genai.interfaces.dto.response.ArchitectureResponse;
import com.arquisoft.genai.interfaces.mapper.ArchitectureMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Architecture Generation", description = "AI-powered software architecture generation")
public class ArchitectureController {

    private final GenerateArchitectureUseCase generateArchitectureUseCase;
    private final ArchitectureMapper architectureMapper;

    @PostMapping("/generate")
    @Operation(
            summary = "Generate software architecture",
            description = """
                    Generates a complete software architecture using AI (GPT-4o-mini) based on:
                    - Business domain
                    - Quality attributes (security, scalability, etc.)
                    - Technology constraints
                    - Free-text description (optional)
                    
                    Returns: architectural style, diagrams (PlantUML/Mermaid), documentation (Markdown),
                    tech stack recommendations, and architectural decisions.
                    
                    **Requires JWT authentication.** Use the Authorize button above.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Architecture generated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT token"),
            @ApiResponse(responseCode = "502", description = "AI provider error (OpenAI unavailable)")
    })
    public ResponseEntity<ArchitectureResponse> generate(
            @Valid @RequestBody ArchitectureGenerationRequest request) {
        ArchitectureInput input = architectureMapper.toInput(request);
        ArchitectureOutput output = generateArchitectureUseCase.generate(input);
        return ResponseEntity.ok(architectureMapper.toResponse(output));
    }
}
