package com.hirehub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hirehub.dto.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Spring Security's default behaviour for an unauthenticated request to a protected
 * endpoint is either an HTTP Basic challenge or a redirect to a login page — neither makes
 * sense for a JSON API. This entry point is what {@code SecurityConfig} registers instead,
 * so a request with no token (or an invalid one) gets the same {@link ErrorResponse} shape
 * as every other error in the app.
 *
 * <p>This fires from inside the security filter chain, before the request ever reaches a
 * controller, which is why it cannot simply throw and let
 * {@code GlobalExceptionHandler} handle it — that advice only sees exceptions thrown during
 * controller execution.</p>
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse body = ErrorResponse.of(HttpStatus.UNAUTHORIZED.value(), "UNAUTHENTICATED",
                "Authentication is required to access this resource", request.getRequestURI());

        objectMapper.writeValue(response.getWriter(), body);
    }
}
