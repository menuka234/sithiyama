package lk.sithiyama.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lk.sithiyama.backend.domain.User;

/** Authentication and account payloads. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 64)
            @Pattern(regexp = "[A-Za-z0-9._-]+", message = "may contain letters, digits, dot, underscore and dash")
            String username,

            @NotBlank @Email @Size(max = 160)
            String email,

            @NotBlank @Size(min = 8, max = 100)
            String password,

            @NotBlank @Size(max = 120)
            String displayName,

            @Size(max = 32)
            String phone,

            @Size(max = 16)
            String locale,

            User.Role role) {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record AuthResponse(
            String accessToken,
            String tokenType,
            long expiresInSeconds,
            UserResponse user) {
    }

    public record UserResponse(
            UUID id,
            String username,
            String email,
            String displayName,
            String phone,
            String locale,
            User.Role role,
            Instant createdAt) {
    }
}
