package com.arquisoft.genai.interfaces.mapper;

import com.arquisoft.genai.application.model.ArchitectureInput;
import com.arquisoft.genai.application.model.ArchitectureOutput;
import com.arquisoft.genai.interfaces.dto.request.ArchitectureGenerationRequest;
import com.arquisoft.genai.interfaces.dto.response.ArchitectureResponse;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper between HTTP DTOs and application-layer models.
 * Keeps the interfaces layer decoupled from the application layer.
 */
@Mapper(componentModel = "spring")
public interface ArchitectureMapper {

    ArchitectureInput toInput(ArchitectureGenerationRequest request);

    ArchitectureResponse toResponse(ArchitectureOutput output);
}
