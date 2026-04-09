package com.arquisoft.genai.interfaces.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "JWT authentication response")
public class AuthResponse {

    @Schema(description = "JWT access token")
    private String token;

    @Schema(description = "Token type", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "Token expiration in seconds", example = "86400")
    private long expiresIn = 86400;

    public AuthResponse(String token) {
        this.token = token;
    }
}
