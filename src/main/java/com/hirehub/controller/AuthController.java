package com.hirehub.controller;

import com.hirehub.dto.auth.AuthResponse;
import com.hirehub.dto.auth.LoginRequest;
import com.hirehub.dto.auth.RegisterRequest;
import com.hirehub.dto.auth.UserSummaryResponse;
import com.hirehub.dto.common.ApiResponse;
import com.hirehub.entity.User;
import com.hirehub.mapper.UserMapper;
import com.hirehub.security.HireHubUserDetails;
import com.hirehub.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * No business logic here beyond the HTTP status to return — see ARCHITECTURE.md §3's rule
 * that controllers stay thin. Every actual decision (uniqueness, credential checking,
 * token issuance) happens in {@link AuthService}.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Registration, login and the current principal")
public class AuthController {

    private final AuthService authService;
    private final UserMapper userMapper;

    public AuthController(AuthService authService, UserMapper userMapper) {
        this.authService = authService;
        this.userMapper = userMapper;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a candidate or recruiter account",
            description = "Creates a User and its matching profile in one transaction. "
                    + "Admin accounts cannot be created through this endpoint.")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Account created successfully", response));
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for a JWT")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.of("Login successful", response));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "The currently authenticated principal")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> me(@AuthenticationPrincipal HireHubUserDetails principal) {
        // The principal was loaded fresh from the database by JwtAuthenticationFilter on
        // this very request (see that class's Javadoc on why it doesn't trust the token's
        // own claims), so this reflects the account's current state, not the state at the
        // time the token was issued.
        User user = principal.getUser();
        return ResponseEntity.ok(ApiResponse.of("Current user", userMapper.toSummary(user)));
    }
}
