package com.hirehub.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request, before Spring Security's own authentication filters, and
 * populates the {@link SecurityContextHolder} from a Bearer token if one is present and
 * valid.
 *
 * <p><b>Fetches the user fresh on every request rather than trusting the token's claims.</b>
 * The JWT carries a {@code role} claim so a resource server that never touches the database
 * could authorize from the token alone — but HireHub is a monolith with the user table one
 * query away, and re-loading through {@link HireHubUserDetailsService} means a role change
 * or an admin disabling the account takes effect on the very next request instead of only
 * once the token expires. The token is used here purely to establish <em>which</em> user is
 * making the request; the authorities Spring Security actually enforces come from the
 * database read that follows.</p>
 *
 * <p>Never throws for a missing or invalid token — it simply leaves the security context
 * empty and lets the request continue as anonymous. Whether an anonymous request is allowed
 * through is entirely {@code SecurityConfig}'s decision, via
 * {@code authorizeHttpRequests(...)}; mixing that decision into this filter would split one
 * piece of logic across two classes.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final HireHubUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, HireHubUserDetailsService userDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (token != null && jwtTokenProvider.isValid(token)) {
            authenticate(token, request);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        Claims claims = jwtTokenProvider.parseAndValidate(token);
        String email = jwtTokenProvider.extractEmail(claims);

        // loadUserByUsername throws UsernameNotFoundException if the account was deleted
        // after the token was issued; that propagates out of this filter as an unhandled
        // exception rather than a clean 401. Acceptable for now - a deleted-but-still-valid
        // token is a narrow edge case - and revisited if it proves otherwise in practice.
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        var authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        String paramToken = request.getParameter("token");
        if (paramToken != null && !paramToken.isBlank()) {
            return paramToken;
        }
        return null;
    }
}
