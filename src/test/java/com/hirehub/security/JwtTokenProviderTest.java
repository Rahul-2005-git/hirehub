package com.hirehub.security;

import com.hirehub.entity.Role;
import com.hirehub.entity.User;
import com.hirehub.entity.enums.RoleName;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Base64;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * No {@code @SpringBootTest}, no database, no container: {@link JwtTokenProvider} depends
 * only on {@link JwtProperties} and the JJWT library, so it is tested as a plain Java
 * object. This is the fast half of the test pyramid the project brief asks for -
 * milliseconds per test, run on every save.
 */
class JwtTokenProviderTest {

    private JwtTokenProvider provider;
    private String secret;

    private static User testUser(Long id, String email, RoleName roleName) throws Exception {
        User user = new User(email, "irrelevant-hash", "Test User", new Role(roleName));
        // The id is normally assigned by the database on insert (GenerationType.IDENTITY),
        // so BaseEntity exposes no public setter. Reflection is the honest way to build a
        // "persisted-looking" user in a unit test without dragging in Spring Data.
        Field idField = findIdField(User.class);
        idField.setAccessible(true);
        idField.set(user, id);
        return user;
    }

    private static Field findIdField(Class<?> type) throws NoSuchFieldException {
        try {
            return type.getDeclaredField("id");
        } catch (NoSuchFieldException e) {
            return findIdField(type.getSuperclass());
        }
    }

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        secret = randomBase64Secret();
        properties.setSecret(secret);
        properties.setExpirationMs(60_000);
        properties.setIssuer("hirehub-test");
        provider = new JwtTokenProvider(properties);
    }

    private String randomBase64Secret() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    @DisplayName("a token generated for a user carries the user's id, email and role")
    void generatedTokenCarriesExpectedClaims() throws Exception {
        User user = testUser(42L, "candidate@hirehub.test", RoleName.CANDIDATE);

        String token = provider.generateToken(user);
        Claims claims = provider.parseAndValidate(token);

        assertThat(provider.extractUserId(claims)).isEqualTo(42L);
        assertThat(provider.extractEmail(claims)).isEqualTo("candidate@hirehub.test");
        assertThat(provider.extractRole(claims)).isEqualTo("CANDIDATE");
        assertThat(claims.getIssuer()).isEqualTo("hirehub-test");
    }

    @Test
    @DisplayName("a freshly generated token is valid")
    void freshTokenIsValid() throws Exception {
        String token = provider.generateToken(testUser(1L, "a@hirehub.test", RoleName.RECRUITER));

        assertThat(provider.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("a token is rejected once it has expired")
    void expiredTokenIsRejected() throws Exception {
        JwtProperties expiredProperties = new JwtProperties();
        expiredProperties.setSecret(randomBase64Secret());
        // A negative expiry backdates the token's expiration to before it was even issued -
        // the simplest deterministic way to produce an already-expired token without a clock
        // abstraction or Thread.sleep in the test.
        expiredProperties.setExpirationMs(-1);
        expiredProperties.setIssuer("hirehub-test");
        JwtTokenProvider expiredTokenProvider = new JwtTokenProvider(expiredProperties);

        String token = expiredTokenProvider.generateToken(testUser(1L, "a@hirehub.test", RoleName.CANDIDATE));

        assertThat(expiredTokenProvider.isValid(token)).isFalse();
        assertThatThrownBy(() -> expiredTokenProvider.parseAndValidate(token))
                .isInstanceOf(io.jsonwebtoken.ExpiredJwtException.class);
    }

    @Test
    @DisplayName("a token signed with a different secret is rejected")
    void tokenSignedWithADifferentSecretIsRejected() throws Exception {
        JwtProperties otherProperties = new JwtProperties();
        otherProperties.setSecret(randomBase64Secret());
        otherProperties.setExpirationMs(60_000);
        otherProperties.setIssuer("hirehub-test");
        JwtTokenProvider otherProvider = new JwtTokenProvider(otherProperties);

        String tokenFromOtherIssuer = otherProvider.generateToken(testUser(1L, "a@hirehub.test", RoleName.CANDIDATE));

        assertThat(provider.isValid(tokenFromOtherIssuer)).isFalse();
    }

    @Test
    @DisplayName("a structurally invalid string is rejected without throwing")
    void garbageTokenIsRejectedWithoutThrowing() {
        assertThat(provider.isValid("not-a-jwt-at-all")).isFalse();
    }

    @Test
    @DisplayName("a token is rejected when the issuer does not match, even with the correct signature")
    void wrongIssuerIsRejected() throws Exception {
        JwtProperties sameSecretDifferentIssuer = new JwtProperties();
        sameSecretDifferentIssuer.setSecret(secret); // identical key material to `provider`
        sameSecretDifferentIssuer.setExpirationMs(60_000);
        sameSecretDifferentIssuer.setIssuer("someone-else");
        JwtTokenProvider otherIssuerProvider = new JwtTokenProvider(sameSecretDifferentIssuer);

        String token = otherIssuerProvider.generateToken(testUser(1L, "a@hirehub.test", RoleName.CANDIDATE));

        // The signature alone would verify fine against `provider`'s key - only
        // requireIssuer() is what makes this fail, proving the issuer check is real.
        assertThat(provider.isValid(token)).isFalse();
    }

    @Test
    @DisplayName("construction fails fast when the configured secret is too short for HS256")
    void tooShortSecretFailsAtConstruction() {
        JwtProperties weakProperties = new JwtProperties();
        weakProperties.setSecret(Base64.getEncoder().encodeToString(new byte[8])); // 64 bits, well under 256
        weakProperties.setExpirationMs(60_000);
        weakProperties.setIssuer("hirehub-test");

        assertThatThrownBy(() -> new JwtTokenProvider(weakProperties))
                .isInstanceOf(WeakKeyException.class);
    }

    @Test
    @DisplayName("expiration is reported in seconds for the auth response")
    void expirationSecondsMatchesConfiguredMilliseconds() {
        assertThat(provider.getExpirationSeconds()).isEqualTo(60);
    }
}
