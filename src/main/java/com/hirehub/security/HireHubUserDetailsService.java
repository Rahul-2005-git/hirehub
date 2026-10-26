package com.hirehub.security;

import com.hirehub.entity.User;
import com.hirehub.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a {@link User} by email and adapts it to {@link HireHubUserDetails}.
 *
 * <p>Registering this as the single {@code UserDetailsService} bean is also what stops
 * Spring Boot's {@code UserDetailsServiceAutoConfiguration} from creating its default
 * in-memory user with a randomly generated password logged at startup.</p>
 *
 * <p><b>Uses {@code findByEmailIgnoreCaseWithRole}, not the plain lookup.</b> See that
 * repository method's Javadoc for the full reasoning: {@code role} is a lazy association,
 * and the caller that matters most — {@code JwtAuthenticationFilter} — reads it with no
 * transaction of its own active by that point. {@code @Transactional(readOnly = true)}
 * below is kept as a second line of defense (any future field added to this path gets the
 * same protection automatically), but it is the {@code JOIN FETCH} that makes the one
 * caller with no transaction boundary at all actually safe.</p>
 */
@Service
@Transactional(readOnly = true)
public class HireHubUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public HireHubUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * "Username" here is the email - HireHub has no separate username field. The exception
     * message intentionally does not repeat the email back: Spring Security's own
     * {@code DaoAuthenticationProvider} converts any lookup failure into the same generic
     * "Bad credentials" response, so this message never reaches an API client, but keeping
     * it free of user-supplied input is still the safer default.
     */
    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmailIgnoreCaseWithRole(email)
                .orElseThrow(() -> new UsernameNotFoundException("No user found for the given email"));
        return new HireHubUserDetails(user);
    }
}
