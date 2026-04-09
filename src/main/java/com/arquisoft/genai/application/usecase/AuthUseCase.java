package com.arquisoft.genai.application.usecase;

import com.arquisoft.genai.application.exception.InvalidCredentialsException;
import com.arquisoft.genai.application.exception.UserAlreadyExistsException;
import com.arquisoft.genai.application.port.AuthService;
import com.arquisoft.genai.domain.model.User;
import com.arquisoft.genai.domain.repository.UserRepository;
import com.arquisoft.genai.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthUseCase implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public String register(String email, String password) {
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("Email already registered: " + email);
        }
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(User.Role.USER)
                .build();
        User saved = userRepository.save(user);
        log.info("New user registered: {}", email);
        return jwtTokenProvider.generateToken(saved.getId().toString(), saved.getRole().name());
    }

    @Override
    public String login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        log.info("User logged in: {}", email);
        return jwtTokenProvider.generateToken(user.getId().toString(), user.getRole().name());
    }
}
