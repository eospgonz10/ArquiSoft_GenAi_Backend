package com.arquisoft.genai.application.port;

import com.arquisoft.genai.application.model.ArchitectureInput;
import com.arquisoft.genai.application.model.ArchitectureOutput;

/**
 * Port (interface) for the AI provider.
 * Allows swapping between OpenAI, Gemini, or Mock implementations
 * without touching business logic.
 */
public interface AiProvider {
    ArchitectureOutput generate(ArchitectureInput input);
}
