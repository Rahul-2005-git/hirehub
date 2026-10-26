package com.hirehub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hirehub.dto.common.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that applies Token-Bucket rate limiting on sensitive public authentication endpoints.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public RateLimitingFilter(RateLimiterService rateLimiterService,
                              ObjectMapper objectMapper,
                              @org.springframework.beans.factory.annotation.Value("${hirehub.rate-limiting.enabled:true}") boolean enabled) {
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();

        // Protect login and register endpoints
        if (uri.startsWith("/api/auth/login") || uri.startsWith("/api/auth/register")) {
            String clientIp = getClientIp(request);

            if (!rateLimiterService.tryAcquire(clientIp, uri)) {
                long retryAfter = rateLimiterService.getRetryAfterSeconds(clientIp, uri);

                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setHeader("Retry-After", String.valueOf(retryAfter));

                ErrorResponse errorBody = ErrorResponse.of(
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        "TOO_MANY_REQUESTS",
                        "Too many attempts. Please try again in " + retryAfter + " seconds.",
                        uri
                );

                response.getWriter().write(objectMapper.writeValueAsString(errorBody));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isBlank()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
