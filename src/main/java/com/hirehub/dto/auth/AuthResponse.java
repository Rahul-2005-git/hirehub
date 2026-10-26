package com.hirehub.dto.auth;

/**
 * @param tokenType always {@code "Bearer"} — included so a client can build the
 *                  {@code Authorization} header without hardcoding the scheme, and so the
 *                  contract already reads correctly if a second token type is ever added.
 * @param expiresIn seconds until the access token expires, from {@code JwtTokenProvider}'s
 *                  configured lifetime — lets a client schedule a re-login instead of
 *                  discovering expiry only from a 401.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserSummaryResponse user
) {
}
