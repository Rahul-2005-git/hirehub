package com.hirehub.service;

import com.hirehub.dto.auth.AuthResponse;
import com.hirehub.dto.auth.LoginRequest;
import com.hirehub.dto.auth.RegisterRequest;
import com.hirehub.dto.auth.RegistrableRole;
import com.hirehub.dto.auth.UserSummaryResponse;
import com.hirehub.entity.CandidateProfile;
import com.hirehub.entity.RecruiterProfile;
import com.hirehub.entity.Role;
import com.hirehub.entity.User;
import com.hirehub.entity.enums.RoleName;
import com.hirehub.exception.DuplicateResourceException;
import com.hirehub.mapper.UserMapper;
import com.hirehub.repository.CandidateProfileRepository;
import com.hirehub.repository.RecruiterProfileRepository;
import com.hirehub.repository.RoleRepository;
import com.hirehub.repository.UserRepository;
import com.hirehub.security.HireHubUserDetails;
import com.hirehub.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code AuthService}'s own collaborators — {@code UserRepository},
 * {@code CandidateProfileRepository}, {@code RecruiterProfileRepository},
 * {@code RoleRepository}, {@code PasswordEncoder}, {@code AuthenticationManager},
 * {@code JwtTokenProvider}, {@code UserMapper} — are all mocked. This is the project
 * brief's "Authentication service" unit-test target: proving the business rules (duplicate
 * email, which profile type gets created, that login delegates entirely to Spring Security)
 * in isolation from Spring and the database, which the integration tests in
 * {@code AuthControllerTest} already cover end-to-end but can't isolate this precisely.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private CandidateProfileRepository candidateProfileRepository;
    @Mock
    private RecruiterProfileRepository recruiterProfileRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private AuthService authService;

    private Role candidateRole;
    private Role recruiterRole;

    @BeforeEach
    void setUp() {
        candidateRole = new Role(RoleName.CANDIDATE);
        recruiterRole = new Role(RoleName.RECRUITER);
    }

    private RegisterRequest registerRequest(String email, RegistrableRole role) {
        return new RegisterRequest(email, "Password123", "Test User", null, role);
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("a new candidate creates a User and a CandidateProfile, in that order, inside one call")
        void registersCandidateSuccessfully() {
            when(userRepository.existsByEmailIgnoreCase("jane@hirehub.test")).thenReturn(false);
            when(roleRepository.findByName(RoleName.CANDIDATE)).thenReturn(Optional.of(candidateRole));
            when(passwordEncoder.encode("Password123")).thenReturn("hashed");
            when(jwtTokenProvider.generateToken(any(User.class))).thenReturn("a-jwt-token");
            when(jwtTokenProvider.getExpirationSeconds()).thenReturn(3600L);
            when(userMapper.toSummary(any(User.class)))
                    .thenReturn(new UserSummaryResponse(1L, "jane@hirehub.test", "Test User", RoleName.CANDIDATE));

            AuthResponse response = authService.register(registerRequest("jane@hirehub.test", RegistrableRole.CANDIDATE));

            assertThat(response.accessToken()).isEqualTo("a-jwt-token");
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.expiresIn()).isEqualTo(3600L);
            verify(userRepository).save(any(User.class));
            verify(candidateProfileRepository).save(any(CandidateProfile.class));
            verify(recruiterProfileRepository, never()).save(any());
        }

        @Test
        @DisplayName("a new recruiter creates a RecruiterProfile, not a CandidateProfile")
        void registersRecruiterSuccessfully() {
            when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
            when(roleRepository.findByName(RoleName.RECRUITER)).thenReturn(Optional.of(recruiterRole));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(jwtTokenProvider.generateToken(any(User.class))).thenReturn("token");
            when(jwtTokenProvider.getExpirationSeconds()).thenReturn(3600L);
            when(userMapper.toSummary(any(User.class)))
                    .thenReturn(new UserSummaryResponse(1L, "r@hirehub.test", "Test User", RoleName.RECRUITER));

            authService.register(registerRequest("r@hirehub.test", RegistrableRole.RECRUITER));

            verify(recruiterProfileRepository).save(any(RecruiterProfile.class));
            verify(candidateProfileRepository, never()).save(any());
        }

        @Test
        @DisplayName("a duplicate email is rejected before any write happens")
        void rejectsDuplicateEmail() {
            when(userRepository.existsByEmailIgnoreCase("taken@hirehub.test")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(registerRequest("taken@hirehub.test", RegistrableRole.CANDIDATE)))
                    .isInstanceOf(DuplicateResourceException.class);

            verify(userRepository, never()).save(any());
            verify(candidateProfileRepository, never()).save(any());
            verify(recruiterProfileRepository, never()).save(any());
        }

        @Test
        @DisplayName("the email is normalized (trimmed and lower-cased) before every check and before saving")
        void normalizesEmailBeforeUse() {
            when(userRepository.existsByEmailIgnoreCase("jane@hirehub.test")).thenReturn(false);
            when(roleRepository.findByName(RoleName.CANDIDATE)).thenReturn(Optional.of(candidateRole));
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(jwtTokenProvider.generateToken(any(User.class))).thenReturn("token");
            when(jwtTokenProvider.getExpirationSeconds()).thenReturn(3600L);
            when(userMapper.toSummary(any(User.class)))
                    .thenReturn(new UserSummaryResponse(1L, "jane@hirehub.test", "Test User", RoleName.CANDIDATE));

            authService.register(registerRequest("  Jane@HireHub.test  ", RegistrableRole.CANDIDATE));

            verify(userRepository).existsByEmailIgnoreCase("jane@hirehub.test");
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getEmail()).isEqualTo("jane@hirehub.test");
        }

        @Test
        @DisplayName("if the seeded role is missing, registration fails loudly rather than silently assigning the wrong role")
        void missingSeededRoleFailsLoudly() {
            when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
            when(roleRepository.findByName(RoleName.CANDIDATE)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.register(registerRequest("x@hirehub.test", RegistrableRole.CANDIDATE)))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("delegates entirely to AuthenticationManager and builds the response from its principal")
        void delegatesToAuthenticationManager() {
            User user = new User("jane@hirehub.test", "hashed", "Jane", candidateRole);
            HireHubUserDetails principal = new HireHubUserDetails(user);
            Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            when(authenticationManager.authenticate(any())).thenReturn(authentication);
            when(jwtTokenProvider.generateToken(user)).thenReturn("a-jwt-token");
            when(jwtTokenProvider.getExpirationSeconds()).thenReturn(3600L);
            when(userMapper.toSummary(user))
                    .thenReturn(new UserSummaryResponse(1L, "jane@hirehub.test", "Jane", RoleName.CANDIDATE));

            AuthResponse response = authService.login(new LoginRequest("jane@hirehub.test", "Password123"));

            assertThat(response.accessToken()).isEqualTo("a-jwt-token");
            ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                    ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
            verify(authenticationManager, times(1)).authenticate(captor.capture());
            // Proves login() does not re-implement password checking itself - it hands the
            // raw credentials straight to the AuthenticationManager and trusts its verdict.
            assertThat(captor.getValue().getCredentials()).isEqualTo("Password123");
        }

        @Test
        @DisplayName("the email is normalized before being handed to AuthenticationManager")
        void normalizesEmailBeforeAuthenticating() {
            User user = new User("jane@hirehub.test", "hashed", "Jane", candidateRole);
            HireHubUserDetails principal = new HireHubUserDetails(user);
            Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            when(authenticationManager.authenticate(any())).thenReturn(authentication);
            when(jwtTokenProvider.generateToken(user)).thenReturn("token");
            when(jwtTokenProvider.getExpirationSeconds()).thenReturn(3600L);
            when(userMapper.toSummary(user))
                    .thenReturn(new UserSummaryResponse(1L, "jane@hirehub.test", "Jane", RoleName.CANDIDATE));

            authService.login(new LoginRequest("  Jane@HireHub.test  ", "Password123"));

            ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                    ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
            verify(authenticationManager).authenticate(captor.capture());
            assertThat(captor.getValue().getPrincipal()).isEqualTo("jane@hirehub.test");
        }
    }
}
