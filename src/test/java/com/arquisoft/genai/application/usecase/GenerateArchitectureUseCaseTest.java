package com.arquisoft.genai.application.usecase;

import com.arquisoft.genai.application.model.ArchitectureInput;
import com.arquisoft.genai.application.model.ArchitectureOutput;
import com.arquisoft.genai.application.port.AiProvider;
import com.arquisoft.genai.application.validation.ArchitectureResponseValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GenerateArchitectureUseCase Tests")
class GenerateArchitectureUseCaseTest {

    @Mock private AiProvider aiProvider;
    @Mock private ArchitectureResponseValidator validator;

    @InjectMocks private GenerateArchitectureUseCase useCase;

    private ArchitectureOutput buildValidOutput() {
        return ArchitectureOutput.builder()
                .style("Microservices")
                .qualityAttributes(List.of("security", "scalability"))
                .diagrams(Map.of("C4-Context", "@startuml\n...\n@enduml"))
                .documentation("# Architecture\nDetails here.")
                .techStack(List.of("Java 17", "Spring Boot 3"))
                .decisions(List.of("Use JWT for auth"))
                .build();
    }

    @Test
    @DisplayName("generate: calls AiProvider and validates result")
    void generate_callsProviderAndValidator() {
        ArchitectureInput input = ArchitectureInput.builder()
                .domain("fintech")
                .qualityAttributes(List.of("security"))
                .build();

        ArchitectureOutput providerOutput = buildValidOutput();
        ArchitectureOutput validatedOutput = buildValidOutput();

        given(aiProvider.generate(input)).willReturn(providerOutput);
        given(validator.validate(providerOutput)).willReturn(validatedOutput);

        ArchitectureOutput result = useCase.generate(input);

        assertThat(result).isEqualTo(validatedOutput);
        then(aiProvider).should(times(1)).generate(input);
        then(validator).should(times(1)).validate(providerOutput);
    }

    @Test
    @DisplayName("generate: returns validated output with correct style")
    void generate_returnsCorrectStyle() {
        ArchitectureInput input = ArchitectureInput.builder().domain("ecommerce").build();
        ArchitectureOutput output = buildValidOutput();

        given(aiProvider.generate(input)).willReturn(output);
        given(validator.validate(output)).willReturn(output);

        ArchitectureOutput result = useCase.generate(input);

        assertThat(result.getStyle()).isEqualTo("Microservices");
        assertThat(result.getTechStack()).contains("Java 17");
    }
}
