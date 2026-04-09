package com.arquisoft.genai.application.port;

/**
 * Port for authentication operations.
 * Returns JWT token strings consumed by the interfaces layer.
 */
public interface AuthService {
    String register(String email, String password);
    String login(String email, String password);
}
