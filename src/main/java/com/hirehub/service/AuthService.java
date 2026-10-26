package com.hirehub.service;

import com.hirehub.dto.auth.AuthResponse;
import com.hirehub.dto.auth.LoginRequest;
import com.hirehub.dto.auth.RegisterRequest;
import com.hirehub.entity.CandidateProfile;
import com.hirehub.entity.RecruiterProfile;
import com.hirehub.entity.Role;
import com.hirehub.entity.User;
import com.hirehub.exception.DuplicateResourceException;
import com.hirehub.mapper.UserMapper;
import com.hirehub.repository.CandidateProfileRepository;
import com.hirehub.repository.RecruiterProfileRepository;
import com.hirehub.repository.RoleRepository;
import com.hirehub.repository.UserRepository;
import com.hirehub.security.HireHubUserDetails;
import com.hirehub.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Registration and login. Everything else auth-related (JWT mechanics, loading a user for
 * Spring Security) already lives in {@code security/} from Phase 4A; this class is the
 * business-rule layer above it.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                        CandidateProfileRepository candidateProfileRepository,
                        RecruiterProfileRepository recruiterProfileRepository,
                        PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager,
                        JwtTokenProvider jwtTokenProvider, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.candidateProfileRepository = candidateProfileRepository;
        this.recruiterProfileRepository = recruiterProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userMapper = userMapper;
    }

    /**
     * Creates a {@code User} and its matching profile row together.
     *
     * <p><b>Transaction boundary</b> (see DATABASE.md §7): a user without a profile is
     * unusable, so both inserts commit or neither does. {@code @Transactional} here is what
     * makes that true — without it, a failure creating the profile would leave a stranded,
     * profile-less user account.</p>
     *
     * <p>The email-uniqueness check below is the service-level half of the same two-layer
     * pattern used for duplicate applications: it gives a clear
     * {@link DuplicateResourceException} for the common case, while
     * {@code uq_users_email} is what actually holds if two registrations for the same
     * email race each other — the loser's insert fails and
     * {@code GlobalExceptionHandler.handleDataIntegrityViolation} turns that into the same
     * 409 the client would have gotten anyway.</p>
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.email());

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DuplicateResourceException("An account with this email already exists");
        }

        Role role = roleRepository.findByName(request.role().toRoleName())
                // Only reachable if migration V2 was never applied or was edited - a
                // configuration error, not a user error, hence unchecked and unhandled here.
                .orElseThrow(() -> new IllegalStateException(
                        "Required role not found: " + request.role() + " - was V2__seed_reference_data.sql applied?"));

        User user = new User(normalizedEmail, passwordEncoder.encode(request.password()),
                request.fullName().trim(), role);
        user.setPhone(blankToNull(request.phone()));
        userRepository.save(user);

        switch (request.role()) {
            case CANDIDATE -> candidateProfileRepository.save(new CandidateProfile(user));
            case RECRUITER -> recruiterProfileRepository.save(new RecruiterProfile(user));
        }

        return buildAuthResponse(user);
    }

    /**
     * Delegates credential checking entirely to Spring Security's
     * {@link AuthenticationManager}: password comparison, the account-enabled check, and
     * the "don't reveal whether the email exists" behaviour all happen inside
     * {@code DaoAuthenticationProvider}, using the {@code HireHubUserDetailsService} and
     * {@code PasswordEncoder} beans from {@code SecurityConfig}. This method does not
     * duplicate any of that logic — duplicating it here would be a second place for the
     * two versions to quietly disagree.
     *
     * <p>{@code readOnly = true}: {@code HireHubUserDetailsService} loads its {@code User}
     * via {@code findByEmailIgnoreCaseWithRole}, which {@code JOIN FETCH}es {@code role} so
     * it is fully materialized — not a lazy proxy — by the time that lookup returns. That is
     * what actually makes {@link #buildAuthResponse}'s read of {@code user.getRole()} safe,
     * regardless of transaction boundaries (see that repository method's Javadoc for the
     * full reasoning, including why {@code JwtAuthenticationFilter} needed the same fix).
     * This method stays {@code @Transactional} anyway as a second line of defense — if a
     * future change to {@link #buildAuthResponse} reads some other, still-lazy association,
     * this boundary is what would keep it working rather than trading one subtle bug for
     * another.</p>
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(normalizeEmail(request.email()), request.password()));

        User user = ((HireHubUserDetails) authentication.getPrincipal()).getUser();
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtTokenProvider.generateToken(user);
        return new AuthResponse(token, "Bearer", jwtTokenProvider.getExpirationSeconds(), userMapper.toSummary(user));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
