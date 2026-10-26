package com.hirehub.security;

import com.hirehub.entity.Role;
import com.hirehub.entity.User;
import com.hirehub.entity.enums.RoleName;
import com.hirehub.repository.RoleRepository;
import com.hirehub.repository.UserRepository;
import com.hirehub.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Deliberately <b>not</b> {@code @Transactional}. Every other repository test in this
 * project uses that annotation so writes roll back automatically — but that would make this
 * specific test worthless: it would silently keep the whole method inside one open
 * transaction/session, exactly the condition that hides the bug this test exists to catch.
 *
 * <p>The real failure mode: {@link UserRepository#save} and
 * {@link UserRepository#findByEmailIgnoreCaseWithRole} are each individually transactional
 * (Spring Data wraps every repository method call in its own transaction by default), and
 * each one's transaction commits and closes the moment that call returns. Calling
 * {@code .getRole().getName()} on the result <em>after</em> the lookup call has returned —
 * exactly what {@code JwtAuthenticationFilter.authenticate()} does when it calls
 * {@code userDetails.getAuthorities()} right after {@code loadUserByUsername(...)} — is the
 * one sequence that would throw {@code LazyInitializationException} if {@code role} were
 * still an uninitialized proxy at that point. A Mockito unit test
 * ({@code HireHubUserDetailsServiceTest}) cannot exercise this at all: its {@code User} and
 * {@code Role} are plain in-memory objects, never real Hibernate proxies, so this is the
 * only place in the test suite that can actually fail the way production would.</p>
 */
class HireHubUserDetailsServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private HireHubUserDetailsService userDetailsService;

    @Test
    @DisplayName("reading the role and authorities after the lookup transaction has closed does not throw")
    void roleSurvivesPastTheLookupsOwnTransaction() {
        Role recruiterRole = roleRepository.findByName(RoleName.RECRUITER).orElseThrow();
        String email = "lazy-role-check-" + UUID.randomUUID() + "@hirehub.test";
        User user = new User(email, "$2a$10$notarealhashnotarealhashnotarealhash", "Lazy Role Test", recruiterRole);

        // This save() call is its own transaction: it has already committed and closed by
        // the time this line finishes, exactly like every write in the real request flow.
        userRepository.save(user);

        // Reproduces JwtAuthenticationFilter.authenticate() verbatim: load through the
        // service (its own, now-closed transaction), then immediately read authorities -
        // no @Transactional anywhere in this test method to paper over the gap.
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        assertThatCode(userDetails::getAuthorities)
                .as("getAuthorities() reads user.getRole().getName() - this must not throw "
                        + "LazyInitializationException once the loading transaction has closed")
                .doesNotThrowAnyException();

        assertThat(userDetails.getAuthorities()).extracting(Object::toString)
                .containsExactly("ROLE_RECRUITER");
    }
}
