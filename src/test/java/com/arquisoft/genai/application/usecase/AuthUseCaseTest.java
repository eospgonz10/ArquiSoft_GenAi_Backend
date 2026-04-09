package com.arquisoft.genai.application.usecase;

import com.arquisoft.genai.application.exception.InvalidCredentialsException;
import com.arquisoft.genai.application.exception.UserAlreadyExistsException;
import com.arquisoft.genai.application.validation.ArchitectureResponseValidator;
import com.arquisoft.genai.domain.model.User;
import com.arquisoft.genai.domain.repository.UserRepository;
import com.arquisoft.genai.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthUseCase Tests")
class AuthUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;

    @InjectMocks private AuthUseCase authUseCase;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .passwordHash("$2a$10$hashed")
                .role(User.Role.USER)
                .build();
    }

    // ─── Register ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: success - new user gets a token")
    void register_success() {
        given(userRepository.existsByEmail("test@example.com")).willReturn(false);
        given(passwordEncoder.encode("password123")).willReturn("$2a$10$hashed");
        given(userRepository.save(any(User.class))).willReturn(mockUser);
        given(jwtTokenProvider.generateToken(anyString(), anyString())).willReturn("jwt-token");

        String token = authUseCase.register("test@example.com", "password123");

        assertThat(token).isEqualTo("jwt-token");
        then(userRepository).should().save(any(User.class));
        then(jwtTokenProvider).should().generateToken(anyString(), eq("USER"));
    }

    @Test
    @DisplayName("register: throws UserAlreadyExistsException for duplicate email")
    void register_duplicateEmail_throwsException() {
        given(userRepository.existsByEmail("test@example.com")).willReturn(true);

        assertThatThrownBy(() -> authUseCase.register("test@example.com", "password123"))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("test@example.com");

        then(userRepository).should(never()).save(any());
    }

    // ─── Login ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: success - correct credentials return token")
    void login_success() {
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(mockUser));
        given(passwordEncoder.matches("password123", "$2a$10$hashed")).willReturn(true);
        given(jwtTokenProvider.generateToken(anyString(), anyString())).willReturn("jwt-token");

        String token = authUseCase.login("test@example.com", "password123");

        assertThat(token).isEqualTo("jwt-token");
    }

    @Test
    @DisplayName("login: throws InvalidCredentialsException when email not found")
    void login_emailNotFound_throwsException() {
        given(userRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> authUseCase.login("unknown@example.com", "password123"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("login: throws InvalidCredentialsException when password is wrong")
    void login_wrongPassword_throwsException() {
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(mockUser));
        given(passwordEncoder.matches("wrongpass", "$2a$10$hashed")).willReturn(false);

        assertThatThrownBy(() -> authUseCase.login("test@example.com", "wrongpass"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
