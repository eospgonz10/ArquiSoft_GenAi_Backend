package com.arquisoft.genai.domain.repository;

import com.arquisoft.genai.domain.model.User;

import java.util.Optional;
import java.util.UUID;

/**
 * Domain port for User persistence.
 * Implementations live in the infrastructure layer.
 */
public interface UserRepository {
    User save(User user);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findById(UUID id);
}
