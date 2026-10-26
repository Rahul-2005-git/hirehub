package com.hirehub.security;

import com.hirehub.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and validates the JWTs that carry authentication between requests.
 *
 * <p><b>Claims.</b> {@code sub} is the user id (a stable, non-PII identifier — never the
 * email, which can change), plus {@code email} and {@code role} so the filter (Phase 4B)
 * can authorize a request without a database round trip. Nothing else goes in the token:
 * no password hash, no phone number, no internal flags. A JWT is base64, not encrypted —
 * anyone holding it can read every claim, so the rule is simple: put nothing in a token you
 * would not put in a URL query parameter.</p>
 *
 * <p><b>HS256, not RS256.</b> HireHub issues and validates tokens from the same process,
 * so a single shared secret is enough. RS256's asymmetric key pair earns its complexity
 * only when a separate service must verify tokens without being able to mint them —
 * not the case here.</p>
 *
 * <p>Deliberately independent of the servlet layer: no {@code HttpServletRequest}, no
 * Spring Security {@code Authentication} type. That keeps it usable from a plain Mockito
 * unit test today and from the {@code JwtAuthenticationFilter} in Phase 4B without either
 * side depending on the other.</p>
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        // Decoded once at construction, not on every call: the secret never changes for
        // the lifetime of the process, and Keys.hmacShaKeyFor validates key length eagerly
        // - a too-short JWT_SECRET fails application startup, not the first login.
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecret()));
    }

    /**
     * @param user must already have its {@code role} association initialized - the caller
     *             (AuthService, Phase 4B) loads the user with its role before calling this
     */
    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(properties.getExpirationMs());

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().getName().name())
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /** Seconds until an access token issued right now would expire. Used in the auth response. */
    public long getExpirationSeconds() {
        return properties.getExpirationMs() / 1000;
    }

    /**
     * Parses and fully verifies a token: signature, expiry and issuer.
     *
     * @throws JwtException if the token is malformed, expired, or signed with a different key.
     *                       The filter in Phase 4B catches this and returns 401 rather than
     *                       letting a malformed Authorization header crash the request.
     */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Non-throwing check for call sites that only need a yes/no answer. Logs at DEBUG, not
     * WARN/ERROR: an expired or tampered token on a public endpoint is routine traffic, not
     * an operational problem, and logging every rejected token at a visible level would
     * make real errors harder to spot in production logs.
     */
    public boolean isValid(String token) {
        try {
            parseAndValidate(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("Rejected expired JWT: {}", e.getMessage());
            return false;
        } catch (SignatureException e) {
            log.debug("Rejected JWT with an invalid signature");
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Rejected malformed JWT: {}", e.getMessage());
            return false;
        }
    }

    public Long extractUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String extractEmail(Claims claims) {
        return claims.get(CLAIM_EMAIL, String.class);
    }

    public String extractRole(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }
}
