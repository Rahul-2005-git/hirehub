package com.hirehub.repository;

import com.hirehub.entity.User;
import com.hirehub.entity.enums.RoleName;
import com.hirehub.entity.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * WHY no derived {@code findByEmail} despite one existing: it is written as
 * {@link #findByEmailIgnoreCase} - the general-purpose lookup, used where the caller only
 * needs {@code User} itself (e.g. the duplicate-email check at registration). The
 * authentication path uses {@link #findByEmailIgnoreCaseWithRole} instead - see that
 * method's Javadoc for why the two are not interchangeable.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Used specifically by {@code HireHubUserDetailsService} on the authentication path.
     *
     * <p><b>Why this exists as a separate method from {@link #findByEmailIgnoreCase}:</b>
     * {@code role} is a {@code LAZY} association (Phase 2B). {@code JwtAuthenticationFilter}
     * calls {@code userDetails.getAuthorities()} — which reads {@code user.getRole()} —
     * immediately after {@code loadUserByUsername(...)} returns, with no
     * {@code @Transactional} boundary of its own wrapping that call. By the time control
     * returns to the filter, the transaction that loaded the {@code User} has already
     * committed and closed, so a lazily-loaded {@code role} would be an uninitialized proxy
     * with no session left to resolve it against — a
     * {@code LazyInitializationException} on every single authenticated request. The
     * {@code JOIN FETCH} here forces {@code Role} to be fully materialized as part of this
     * one query, so the returned {@code User} is safe to read {@code getRole()} from at any
     * point afterward, regardless of what transaction boundary is or isn't active by then.</p>
     */
    @Query("select u from User u join fetch u.role where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCaseWithRole(@Param("email") String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Admin user-management screen: filter by role and/or status without a combinatorial
     * set of derived methods. Both filters are optional — pass {@code null} to skip one.
     * A JPQL query is enough here (unlike job search, which has far more optional filters
     * and therefore uses a Specification in Phase 9).
     */
    @Query("""
            select u from User u
            where (:role is null or u.role.name = :role)
              and (:status is null or u.status = :status)
            """)
    Page<User> search(@Param("role") RoleName role, @Param("status") UserStatus status, Pageable pageable);
}
