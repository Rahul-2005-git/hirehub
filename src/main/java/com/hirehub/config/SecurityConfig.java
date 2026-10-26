package com.hirehub.config;

import com.hirehub.security.JwtAccessDeniedHandler;
import com.hirehub.security.JwtAuthenticationEntryPoint;
import com.hirehub.security.JwtAuthenticationFilter;
import com.hirehub.security.RateLimitingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * The security filter chain, plus method security for role-based rules.
 *
 * <p><b>Two layers, deliberately.</b> {@code authorizeHttpRequests} below stays coarse —
 * it only ever distinguishes public endpoints from "needs a valid token" — while
 * {@code @PreAuthorize} on individual controller methods (starting with
 * {@code CandidateProfileController} in Phase 6) does the fine-grained
 * "which role" decision, right next to the method it protects. This is the split promised
 * in ARCHITECTURE.md §5: URL rules for coarse gates, method-level checks for everything
 * resource-specific. Putting a role check in a URL matcher instead of on the method would
 * separate the rule from the code it governs, and a wildcard path is a blunter instrument
 * than an annotation that reads directly against the method signature.</p>
 *
 * <p>{@code @EnableMethodSecurity} (not the older {@code @EnableGlobalMethodSecurity}) is
 * what makes {@code @PreAuthorize} active at all — without it, the annotations would be
 * silently ignored rather than enforced.</p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                           RateLimitingFilter rateLimitingFilter,
                           JwtAuthenticationEntryPoint authenticationEntryPoint,
                           JwtAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    /**
     * BCrypt: an adaptive hash with a per-password salt built in, purpose-built for
     * passwords rather than general-purpose hashing (unlike SHA-256, which is fast — a
     * property that helps an attacker brute-forcing stolen hashes, not the defender).
     * The work factor defaults to 10; strength is not hardcoded to a specific round count
     * for the same reason the JWT secret is not hardcoded — the safe default should not
     * require a code change to raise later as hardware gets faster.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Exposes the {@code AuthenticationManager} Spring Boot assembles internally so
     * {@code AuthService.login()} can call it directly, instead of hand-rolling password
     * comparison. This is what wires {@code HireHubUserDetailsService} and
     * {@code passwordEncoder()} together into the actual login check.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // A JWT in an Authorization header is immune to CSRF, which exploits a
                // browser automatically attaching cookies to a cross-site request. CSRF
                // protection is a cookie/session-auth concern; disabling it here is not a
                // shortcut, it is the correct configuration for a stateless bearer-token API.
                .csrf(AbstractHttpConfigurer::disable)
                // No HttpSession is ever created or read: every request must carry its own
                // proof of identity. This is also what makes horizontal scaling trivial -
                // any instance can serve any request, with no sticky-session requirement.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/api/health", "/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                        // A job-board's company and job listings are browsable without an
                        // account; only creating or editing needs a recruiter's token.
                        // HttpMethod.GET scopes this to reads only - POST/PUT/PATCH/DELETE
                        // to these same paths still fall through to anyRequest().authenticated()
                        // below, and JobController's own service layer additionally hides a
                        // DRAFT job from a public GET regardless of this rule (see
                        // JobService.getById) - this matcher only controls whether the
                        // request reaches the controller at all, not what it's allowed to see.
                        .requestMatchers(HttpMethod.GET, "/api/companies/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/jobs", "/api/jobs/*").permitAll()
                        // The one URL-level role rule in the app, exactly as ARCHITECTURE.md
                        // §5 sketched it in Phase 0: an entire namespace reserved for one
                        // role is simpler to express as a path prefix than as @PreAuthorize
                        // repeated on every current and future admin controller method.
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Coarse-grained: everything else just needs a valid token. Which
                        // role can do what is decided by @PreAuthorize on the resource's
                        // own controller method, not here.
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                // Runs rate limiting first on auth endpoints
                .addFilterBefore(rateLimitingFilter, org.springframework.security.web.authentication.logout.LogoutFilter.class)
                // Runs before Spring Security's own username/password filter so a request
                // already carrying a valid JWT is authenticated before that filter would
                // otherwise look for session-based credentials.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
