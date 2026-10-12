package lk.sithiyama.backend.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT settings, bound from {@code sithiyama.jwt.*}.
 *
 * @param secret        HS256 signing key; must be at least 32 bytes
 * @param issuer        value written to and required in the {@code iss} claim
 * @param accessTokenTtl how long an issued access token stays valid
 */
@ConfigurationProperties(prefix = "sithiyama.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTokenTtl) {
}
