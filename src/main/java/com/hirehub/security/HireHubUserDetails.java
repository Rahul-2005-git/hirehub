package com.hirehub.security;

import com.hirehub.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapts {@link User} to the interface Spring Security's authentication machinery expects.
 *
 * <p>Kept as a thin, separate class rather than making {@code User} implement
 * {@code UserDetails} directly: the entity stays a plain persistence model, free of
 * framework interfaces baked into its type signature, and this adapter is the only place
 * that needs to know how a domain role maps to a Spring Security authority.</p>
 *
 * <p><b>Authority naming.</b> Spring Security's {@code hasRole('X')} expects an authority
 * literally named {@code ROLE_X}; {@code hasAuthority('X')} expects the bare name. HireHub
 * standardises on {@code hasRole(...)} everywhere in {@code SecurityConfig} and
 * {@code @PreAuthorize} (Phase 5), so the {@code ROLE_} prefix is added here, once, rather
 * than repeated - and easy to forget - at every authorization check.</p>
 */
public class HireHubUserDetails implements UserDetails {

    private final User user;

    public HireHubUserDetails(User user) {
        this.user = user;
    }

    /** Escape hatch for code that needs the real entity - e.g. to load @Id for a service call. */
    public User getUser() {
        return user;
    }

    public Long getUserId() {
        return user.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName().name()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    /** Spring Security's "username" is HireHub's email - there is no separate username field. */
    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** The only account-state flag HireHub actually uses: an admin-disabled user. */
    @Override
    public boolean isEnabled() {
        return user.isActive();
    }
}
