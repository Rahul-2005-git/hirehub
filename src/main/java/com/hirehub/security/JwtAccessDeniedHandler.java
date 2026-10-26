package com.hirehub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hirehub.dto.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * The filter-chain counterpart to {@link JwtAuthenticationEntryPoint}: fires when a request
 * is authenticated but the principal lacks the required role or ownership. Not exercised by
 * any rule yet — {@code SecurityConfig} only distinguishes authenticated from anonymous
 * until Phase 5 adds {@code hasRole(...)} matchers and {@code @PreAuthorize} — but wired now
 * so the 403 response shape is already correct and tested the moment it is.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse body = ErrorResponse.of(HttpStatus.FORBIDDEN.value(), "ACCESS_DENIED",
                "You do not have permission to perform this action", request.getRequestURI());

        objectMapper.writeValue(response.getWriter(), body);
    }
}
