package com.arquisoft.genai.application.usecase;

import com.arquisoft.genai.application.model.ArchitectureInput;
import com.arquisoft.genai.application.model.ArchitectureOutput;
import com.arquisoft.genai.application.port.AiProvider;
import com.arquisoft.genai.application.validation.ArchitectureResponseValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenerateArchitectureUseCase {

    private final AiProvider aiProvider;
    private final ArchitectureResponseValidator validator;

    public ArchitectureOutput generate(ArchitectureInput input) {
        log.info("Generating architecture for domain: '{}'", input.getDomain());
        ArchitectureOutput output = aiProvider.generate(input);
        return validator.validate(output);
    }
}
