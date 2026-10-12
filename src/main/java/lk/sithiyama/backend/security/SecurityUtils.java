package lk.sithiyama.backend.security;

import java.util.Optional;
import java.util.UUID;
import lk.sithiyama.backend.exception.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Convenience access to the current {@link AppUserPrincipal}. */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<AppUserPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AppUserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    /** The current principal, or a 403 if the request is anonymous. */
    public static AppUserPrincipal requirePrincipal() {
        return currentPrincipal()
                .orElseThrow(() -> new ForbiddenException("This operation requires an authenticated account"));
    }

    public static UUID requireUserId() {
        return requirePrincipal().id();
    }

    public static boolean isAuthenticated() {
        return currentPrincipal().isPresent();
    }
}
