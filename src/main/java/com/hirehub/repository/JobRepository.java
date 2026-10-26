package com.hirehub.repository;

import com.hirehub.entity.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * {@link JpaSpecificationExecutor} is added now, in Phase 3, even though no
 * {@code Specification} exists until Phase 9. WHY here rather than there: it is a property
 * of the repository's shape, and declaring it alongside the query methods keeps every data
 * -access capability for {@code Job} in one interface instead of splitting it across phases
 * for no reason. This is what {@code jobRepository.findAll(spec, pageable)} calls, from
 * {@code JobService.search} — Phase 9's job search.
 *
 * <p>No derived methods like {@code findByLocationAndEmploymentTypeAndExperienceLevel} are
 * added — that is exactly the combinatorial-explosion approach the search design in
 * ARCHITECTURE.md §7 rejects. Simple, single-purpose lookups stay below; everything
 * combinable goes through the Specification.</p>
 *
 * <p>Two methods that were speculatively added here in earlier phases —
 * {@code findByCompanyId} ("all jobs posted by my company") and {@code findByStatus} (the
 * public feed, before Phase 9's Specification search replaced it) — were removed once
 * Phase 9 confirmed neither was actually called from anywhere. Same reasoning as dropping
 * {@code JWT_REFRESH_EXPIRATION_MS} in Phase 4A: unused capability that looks finished
 * invites more confidence in it than it deserves. Either can be added back in one line
 * the moment a real endpoint needs it.</p>
 */
public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {

    /**
     * Job detail page: one round trip for the job, its company and its required skills.
     * {@code @EntityGraph} is preferred here over a JPQL {@code JOIN FETCH} because it
     * needs no query string — Hibernate builds the join from the attribute paths, which
     * stays correct if a field is renamed.
     */
    @EntityGraph(attributePaths = {"company", "requiredSkills", "requiredSkills.skill"})
    Optional<Job> findWithDetailsById(Long id);

    /**
     * Specification-based search with eager loading of company and required skills.
     * Without this override, {@code JpaSpecificationExecutor.findAll(Specification, Pageable)}
     * would perform N+1 lazy-load queries for each job's company and skill set.
     * The {@code @EntityGraph} here ensures a single JOIN query loads everything the
     * {@link com.hirehub.mapper.JobMapper} needs without additional round trips.
     */
    @EntityGraph(attributePaths = {"company", "requiredSkills", "requiredSkills.skill"})
    Page<Job> findAll(Specification<Job> spec, Pageable pageable);

    /** "My own postings", regardless of which company they were created under. */
    @EntityGraph(attributePaths = {"company", "requiredSkills", "requiredSkills.skill"})
    Page<Job> findByCreatedById(Long recruiterProfileId, Pageable pageable);
}
