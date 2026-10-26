package com.hirehub.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Binds {@code hirehub.jwt.*} from application.yml (ultimately from {@code JWT_SECRET},
 * {@code JWT_EXPIRATION_MS}, {@code JWT_ISSUER}).
 *
 * <p>A typed properties class instead of scattering {@code @Value("${hirehub.jwt.secret}")}
 * across {@link JwtTokenProvider}: the binding is validated once at startup
 * ({@code @Validated} + Bean Validation means a blank secret fails the context refresh with
 * a clear message, not a null pointer on the first login attempt), and every JWT setting
 * lives in one place.</p>
 */
@Component
@ConfigurationProperties(prefix = "hirehub.jwt")
@Validated
public class JwtProperties {

    /** Base64-encoded HMAC signing key. Must never have a hardcoded default - see application.yml. */
    @NotBlank
    private String secret;

    @Positive
    private long expirationMs;

    @NotBlank
    private String issuer;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
