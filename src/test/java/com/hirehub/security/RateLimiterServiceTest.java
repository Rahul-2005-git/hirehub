package com.hirehub.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RateLimiterService();
    }

    @Test
    @DisplayName("Allows requests up to the login limit (10 requests) and blocks the 11th")
    void loginRateLimiterLimitsAfterTenAttempts() {
        String clientIp = "192.168.1.100";
        String loginUri = "/api/auth/login";

        // First 10 requests should succeed
        for (int i = 0; i < 10; i++) {
            boolean acquired = rateLimiterService.tryAcquire(clientIp, loginUri);
            assertThat(acquired)
                    .as("Attempt %d should be allowed", i + 1)
                    .isTrue();
        }

        // 11th request should be blocked
        boolean eleventhAttempt = rateLimiterService.tryAcquire(clientIp, loginUri);
        assertThat(eleventhAttempt)
                .as("11th attempt should be blocked by rate limiter")
                .isFalse();

        // Retry-After should be greater than 0
        long retryAfter = rateLimiterService.getRetryAfterSeconds(clientIp, loginUri);
        assertThat(retryAfter).isGreaterThan(0L);
    }

    @Test
    @DisplayName("Allows requests up to the register limit (5 requests) and blocks the 6th")
    void registerRateLimiterLimitsAfterFiveAttempts() {
        String clientIp = "192.168.1.101";
        String registerUri = "/api/auth/register";

        // First 5 requests should succeed
        for (int i = 0; i < 5; i++) {
            boolean acquired = rateLimiterService.tryAcquire(clientIp, registerUri);
            assertThat(acquired)
                    .as("Attempt %d should be allowed", i + 1)
                    .isTrue();
        }

        // 6th attempt should be blocked
        boolean sixthAttempt = rateLimiterService.tryAcquire(clientIp, registerUri);
        assertThat(sixthAttempt)
                .as("6th attempt should be blocked")
                .isFalse();
    }

    @Test
    @DisplayName("Different IP addresses maintain separate rate limit buckets")
    void differentIpsHaveIndependentBuckets() {
        String ip1 = "10.0.0.1";
        String ip2 = "10.0.0.2";
        String uri = "/api/auth/register";

        // Exhaust IP 1
        for (int i = 0; i < 5; i++) {
            rateLimiterService.tryAcquire(ip1, uri);
        }
        assertThat(rateLimiterService.tryAcquire(ip1, uri)).isFalse();

        // IP 2 should still be allowed
        assertThat(rateLimiterService.tryAcquire(ip2, uri)).isTrue();
    }
}
