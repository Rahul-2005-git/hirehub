package com.hirehub.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Token-Bucket Rate Limiter protecting sensitive authentication endpoints
 * against brute-force password guessing and spam registration attacks.
 */
@Service
public class RateLimiterService {

    // Capacity and refill window
    private static final int LOGIN_MAX_REQUESTS = 10;
    private static final long LOGIN_WINDOW_SECONDS = 60;

    private static final int REGISTER_MAX_REQUESTS = 5;
    private static final long REGISTER_WINDOW_SECONDS = 60;

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public boolean tryAcquire(String clientIp, String uri) {
        String key = clientIp + ":" + uri;
        int maxCapacity;
        long windowSeconds;

        if (uri.contains("/auth/register")) {
            maxCapacity = REGISTER_MAX_REQUESTS;
            windowSeconds = REGISTER_WINDOW_SECONDS;
        } else {
            maxCapacity = LOGIN_MAX_REQUESTS;
            windowSeconds = LOGIN_WINDOW_SECONDS;
        }

        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(maxCapacity, windowSeconds));
        return bucket.tryConsume();
    }

    public long getRetryAfterSeconds(String clientIp, String uri) {
        String key = clientIp + ":" + uri;
        TokenBucket bucket = buckets.get(key);
        return bucket != null ? bucket.getSecondsUntilRefill() : 60;
    }

    private static class TokenBucket {
        private final int capacity;
        private final long windowSeconds;
        private int tokens;
        private Instant lastRefillTimestamp;

        public TokenBucket(int capacity, long windowSeconds) {
            this.capacity = capacity;
            this.windowSeconds = windowSeconds;
            this.tokens = capacity;
            this.lastRefillTimestamp = Instant.now();
        }

        public synchronized boolean tryConsume() {
            refill();
            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }

        public synchronized long getSecondsUntilRefill() {
            refill();
            if (tokens > 0) return 0;
            long elapsed = Instant.now().getEpochSecond() - lastRefillTimestamp.getEpochSecond();
            return Math.max(1, windowSeconds - elapsed);
        }

        private void refill() {
            Instant now = Instant.now();
            long elapsedSeconds = now.getEpochSecond() - lastRefillTimestamp.getEpochSecond();
            if (elapsedSeconds >= windowSeconds) {
                tokens = capacity;
                lastRefillTimestamp = now;
            }
        }
    }
}
