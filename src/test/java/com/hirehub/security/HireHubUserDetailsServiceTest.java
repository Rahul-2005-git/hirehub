package com.hirehub.security;

import com.hirehub.entity.Role;
import com.hirehub.entity.User;
import com.hirehub.entity.enums.RoleName;
import com.hirehub.entity.enums.UserStatus;
import com.hirehub.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code UserRepository} is mocked rather than backed by a real database: this class has
 * exactly one collaborator and one branch of logic per test, which is precisely what
 * Mockito is for. Repository behaviour itself is already covered by
 * {@code UserRepositoryTest} in Phase 3.
 *
 * <p>Stubs {@code findByEmailIgnoreCaseWithRole}, not {@code findByEmailIgnoreCase} — the
 * service was changed to call the eager-fetch variant (see that method's Javadoc for why),
 * and a Mockito unit test like this one cannot catch a lazy-loading regression on its own:
 * the {@code User}/{@code Role} objects here are plain in-memory objects, never real
 * Hibernate proxies, so a mismatch between which method is stubbed and which the service
 * actually calls would only be caught by the tests still passing for the wrong reason
 * (return Optional.empty() by Mockito's default and every test coincidentally still failing
 * loudly with the right exception) rather than a true regression signal. Kept in sync with
 * the service by hand for that reason.</p>
 */
@ExtendWith(MockitoExtension.class)
class HireHubUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private HireHubUserDetailsService service;

    private User activeCandidate() {
        return new User("candidate@hirehub.test", "$2a$10$hash", "Jane Candidate", new Role(RoleName.CANDIDATE));
    }

    @Test
    @DisplayName("loads a user by email and adapts it to Spring Security's UserDetails")
    void loadsUserByEmail() {
        when(userRepository.findByEmailIgnoreCaseWithRole("candidate@hirehub.test"))
                .thenReturn(Optional.of(activeCandidate()));

        UserDetails result = service.loadUserByUsername("candidate@hirehub.test");

        assertThat(result.getUsername()).isEqualTo("candidate@hirehub.test");
        assertThat(result.getPassword()).isEqualTo("$2a$10$hash");
        assertThat(result.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_CANDIDATE");
        assertThat(result).isInstanceOf(HireHubUserDetails.class);
    }

    @Test
    @DisplayName("an unknown email raises UsernameNotFoundException, not a null result")
    void unknownEmailThrows() {
        when(userRepository.findByEmailIgnoreCaseWithRole(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nobody@hirehub.test"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("a disabled user is still loaded, but reports isEnabled() = false")
    void disabledUserIsLoadedButReportsDisabled() {
        User disabled = activeCandidate();
        disabled.setStatus(UserStatus.DISABLED);
        when(userRepository.findByEmailIgnoreCaseWithRole("candidate@hirehub.test"))
                .thenReturn(Optional.of(disabled));

        UserDetails result = service.loadUserByUsername("candidate@hirehub.test");

        // Loading succeeds - it is Spring Security's DaoAuthenticationProvider that checks
        // isEnabled() and rejects the login. Keeping that check out of this class means the
        // "why was I rejected" decision lives in exactly one place (the auth provider).
        assertThat(result.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("the lookup is delegated to the repository exactly once, by the given email")
    void delegatesToRepositoryOnce() {
        when(userRepository.findByEmailIgnoreCaseWithRole("candidate@hirehub.test"))
                .thenReturn(Optional.of(activeCandidate()));

        service.loadUserByUsername("candidate@hirehub.test");

        verify(userRepository).findByEmailIgnoreCaseWithRole("candidate@hirehub.test");
    }
}
