package lk.sithiyama.backend.service;

import java.util.Locale;
import lk.sithiyama.backend.domain.User;
import lk.sithiyama.backend.dto.AuthDtos.AuthResponse;
import lk.sithiyama.backend.dto.AuthDtos.LoginRequest;
import lk.sithiyama.backend.dto.AuthDtos.RegisterRequest;
import lk.sithiyama.backend.dto.AuthDtos.UserResponse;
import lk.sithiyama.backend.exception.BadRequestException;
import lk.sithiyama.backend.exception.DuplicateResourceException;
import lk.sithiyama.backend.exception.ResourceNotFoundException;
import lk.sithiyama.backend.repository.UserRepository;
import lk.sithiyama.backend.security.AppUserPrincipal;
import lk.sithiyama.backend.security.JwtService;
import lk.sithiyama.backend.security.SecurityUtils;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration, sign-in and the "who am I" endpoint. */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new DuplicateResourceException("Username '" + username + "' is already taken");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Email '" + email + "' is already registered");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setPhone(request.phone());
        user.setLocale(request.locale() == null || request.locale().isBlank() ? "en" : request.locale());
        user.setRole(resolveSelfAssignableRole(request.role()));
        user.setEnabled(true);

        return issueToken(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username().trim(), request.password()));
        AppUserPrincipal principal = (AppUserPrincipal) authentication.getPrincipal();
        User user = userRepository.findById(principal.id())
                .orElseThrow(() -> new ResourceNotFoundException("Account no longer exists"));
        return issueToken(user);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser() {
        User user = userRepository.findById(SecurityUtils.requireUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Account no longer exists"));
        return toUserResponse(user);
    }

    private User.Role resolveSelfAssignableRole(User.Role requested) {
        if (requested == null) {
            return User.Role.ROLE_HIKER;
        }
        if (requested == User.Role.ROLE_ADMIN) {
            throw new BadRequestException("ROLE_ADMIN cannot be self-assigned");
        }
        return requested;
    }

    private AuthResponse issueToken(User user) {
        String token = jwtService.generateToken(AppUserPrincipal.from(user));
        return new AuthResponse(token, "Bearer", jwtService.expiresInSeconds(), toUserResponse(user));
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getDisplayName(),
                user.getPhone(),
                user.getLocale(),
                user.getRole(),
                user.getCreatedAt());
    }
}
