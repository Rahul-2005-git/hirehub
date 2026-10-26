package com.hirehub.repository;

import com.hirehub.entity.CandidateProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CandidateProfileRepository extends JpaRepository<CandidateProfile, Long> {

    Optional<CandidateProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    /**
     * The "me" equivalent of {@link #findByIdWithSkills}: the candidate's own profile view
     * always needs the skills, so this is the query {@code /api/candidates/me} actually
     * uses. Kept as a separate method rather than looking up the profile id first and
     * reusing {@link #findByIdWithSkills} — that would be two round trips for one page.
     */
    @Query("select distinct cp from CandidateProfile cp "
            + "left join fetch cp.skills s "
            + "left join fetch s.skill "
            + "where cp.user.id = :userId")
    Optional<CandidateProfile> findByUserIdWithSkills(@Param("userId") Long userId);

    /**
     * {@code JOIN FETCH} loads the profile and its skills in one round trip instead of the
     * classic N+1: a lazy {@code skills} collection fetched only when
     * {@code getSkills()} is first called inside the service. Used specifically by the
     * "view my full profile" endpoint (Phase 6), which always needs the skills; list views
     * that don't need them use {@link #findByUserId} instead, so this is the exception, not
     * the default.
     */
    @Query("select distinct cp from CandidateProfile cp "
            + "left join fetch cp.skills s "
            + "left join fetch s.skill "
            + "where cp.id = :id")
    Optional<CandidateProfile> findByIdWithSkills(@Param("id") Long id);
}
