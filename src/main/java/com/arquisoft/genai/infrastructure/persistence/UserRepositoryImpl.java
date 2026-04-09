package com.arquisoft.genai.infrastructure.persistence;

import com.arquisoft.genai.domain.model.User;
import com.arquisoft.genai.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter: implements the domain UserRepository port using Spring Data JPA.
 */
@Component
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final SpringUserRepository springUserRepository;

    @Override
    public User save(User user) {
        return springUserRepository.save(user);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return springUserRepository.findByEmail(email);
    }

    @Override
    public boolean existsByEmail(String email) {
        return springUserRepository.existsByEmail(email);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return springUserRepository.findById(id);
    }
}
