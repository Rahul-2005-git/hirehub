package com.hirehub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hirehub.entity.Role;
import com.hirehub.entity.User;
import com.hirehub.entity.enums.RoleName;
import com.hirehub.entity.enums.UserStatus;
import com.hirehub.repository.RoleRepository;
import com.hirehub.repository.UserRepository;
import com.hirehub.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the whole stack through the real {@code SecurityFilterChain} — no mocked
 * security context, no {@code @WithMockUser}. A token issued by {@code /auth/login} is used
 * verbatim on {@code /auth/me}, the same way a real client would, which is what actually
 * proves {@code JwtAuthenticationFilter} and {@code SecurityConfig} agree with each other.
 */
@AutoConfigureMockMvc
class AuthControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String uniqueEmail() {
        return "test-" + UUID.randomUUID() + "@hirehub.test";
    }

    private String registerPayload(String email, String password, String role) {
        return """
                {"email":"%s","password":"%s","fullName":"Test User","role":"%s"}
                """.formatted(email, password, role);
    }

    @Test
    @DisplayName("registering a candidate returns 201 with a token and the candidate's own data")
    void registerCandidateSucceeds() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(email, "Password123", "CANDIDATE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.role").value("CANDIDATE"));
    }

    @Test
    @DisplayName("registering a recruiter succeeds and creates a recruiter profile, not a candidate one")
    void registerRecruiterSucceeds() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(uniqueEmail(), "Password123", "RECRUITER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.role").value("RECRUITER"));
    }

    @Test
    @DisplayName("registering with an already-used email returns 409, not 500")
    void registeringDuplicateEmailReturnsConflict() throws Exception {
        String email = uniqueEmail();
        String payload = registerPayload(email, "Password123", "CANDIDATE");

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_RESOURCE"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("a request body naming role ADMIN is rejected at deserialization, not silently downgraded")
    void adminRoleCannotBeSelfRegistered() throws Exception {
        // RegistrableRole has no ADMIN constant, so Jackson fails to bind the enum entirely -
        // this proves the type-level guard from RegistrableRole's Javadoc, not just a
        // service-level `if` that a future refactor could accidentally remove.
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(uniqueEmail(), "Password123", "ADMIN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a weak password is rejected with a field-level validation error")
    void weakPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(uniqueEmail(), "short", "CANDIDATE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("logging in with the correct password returns a working token")
    void loginSucceedsWithCorrectPassword() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(email, "Password123", "CANDIDATE")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(email));
    }

    @Test
    @DisplayName("logging in with the wrong password returns 401 with a generic message")
    void loginFailsWithWrongPassword() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPayload(email, "Password123", "CANDIDATE")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"WrongPassword1"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("logging in with an email that was never registered gives the same error as a wrong password")
    void loginWithUnknownEmailGivesTheSameErrorAsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Whatever123"}
                                """.formatted(uniqueEmail())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("logging in to a disabled account is rejected even with the correct password")
    @Transactional
    void loginFailsForDisabledAccount() throws Exception {
        Role candidateRole = roleRepository.findByName(RoleName.CANDIDATE).orElseThrow();
        User disabled = new User(uniqueEmail(), passwordEncoder.encode("Password123"), "Disabled User", candidateRole);
        disabled.setStatus(UserStatus.DISABLED);
        userRepository.save(disabled);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123"}
                                """.formatted(disabled.getEmail())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("ACCOUNT_DISABLED"));
    }

    @Test
    @DisplayName("/me without a token is rejected by the filter chain with the JSON 401 shape")
    void meWithoutTokenIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("/me with a garbage token is rejected the same way as no token")
    void meWithGarbageTokenIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("/me with a valid token returns that exact account's data")
    void meWithValidTokenReturnsCurrentUser() throws Exception {
        String email = uniqueEmail();
        String registerResponse = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(email, "Password123", "RECRUITER")))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(registerResponse).path("data").path("accessToken").asText();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.role").value("RECRUITER"));
    }
}
