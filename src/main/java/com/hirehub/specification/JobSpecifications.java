package com.hirehub.specification;

import com.hirehub.entity.Job;
import com.hirehub.entity.JobSkill;
import com.hirehub.entity.Skill;
import com.hirehub.entity.enums.EmploymentType;
import com.hirehub.entity.enums.ExperienceLevel;
import com.hirehub.entity.enums.JobStatus;
import jakarta.persistence.criteria.Join;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/**
 * One small, composable {@link Specification} per filter, combined with {@code and()} by
 * {@code JobService.search}. This is the design ARCHITECTURE.md §7 committed to back in
 * Phase 0 and {@code JobRepository} was already wired for in Phase 3: the alternative — a
 * derived method per filter combination — would be combinatorial (seven independent filters
 * here would mean up to 2^7 possible method names), and string-built SQL would reopen an
 * injection risk this whole approach exists to close.
 *
 * <p>Every method treats a missing/blank filter value as "no constraint" by returning
 * {@code cb.conjunction()} (an always-true predicate) rather than {@code null} — a
 * {@code null} {@link jakarta.persistence.criteria.Predicate} passed into {@code cb.and(...)}
 * would throw, so this keeps every predicate safe to compose unconditionally without a
 * null-check at each call site.</p>
 */
public final class JobSpecifications {

    private JobSpecifications() {
        // static factory methods only
    }

    public static Specification<Job> hasStatus(JobStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    /**
     * Matches {@code idx_jobs_title_trgm} (DATABASE.md §6), which is a GIN trigram index
     * built on the raw {@code title} column. WHY {@code ilike}, not {@code lower(title) like
     * lower(?)}: the latter is a different SQL expression than what the index was built on —
     * PostgreSQL will not use a trigram index on {@code title} to satisfy a predicate on
     * {@code lower(title)}. {@code ilike} is not part of the portable
     * {@code jakarta.persistence.criteria.CriteriaBuilder} interface, so this casts to
     * Hibernate's own {@link HibernateCriteriaBuilder}, which is always the actual runtime
     * implementation here — the project already commits to PostgreSQL-specific SQL
     * elsewhere (the index itself, {@code pg_trgm}), so this is one more explicit,
     * documented instance of that same coupling, not a new kind of risk.
     */
    public static Specification<Job> hasKeyword(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return cb.conjunction();
            }
            HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
            return hcb.ilike(root.get("title"), "%" + keyword.trim() + "%");
        };
    }

    /**
     * Matches {@code idx_jobs_location} (DATABASE.md §6), a functional index on
     * {@code lower(location)} — so this predicate, unlike {@link #hasKeyword}, deliberately
     * *does* use {@code lower(...)} on both sides, matching that index's expression exactly.
     */
    public static Specification<Job> hasLocation(String location) {
        return (root, query, cb) -> location == null || location.isBlank()
                ? cb.conjunction()
                : cb.equal(cb.lower(root.get("location")), location.trim().toLowerCase());
    }

    public static Specification<Job> hasEmploymentType(EmploymentType employmentType) {
        return (root, query, cb) -> employmentType == null
                ? cb.conjunction()
                : cb.equal(root.get("employmentType"), employmentType);
    }

    public static Specification<Job> hasExperienceLevel(ExperienceLevel experienceLevel) {
        return (root, query, cb) -> experienceLevel == null
                ? cb.conjunction()
                : cb.equal(root.get("experienceLevel"), experienceLevel);
    }

    /**
     * "Could this job pay at least what I'm looking for" — matched against {@code salaryMax},
     * not {@code salaryMin}: a job listed as 15-25 LPA should match a candidate filtering for
     * 20+, even though its minimum is below that. A job with no salary range at all
     * ({@code salaryMax IS NULL}) is excluded when this filter is active — there is nothing
     * to verify the match against, and silently including it would be a false positive.
     */
    public static Specification<Job> hasMinSalary(BigDecimal minSalary) {
        return (root, query, cb) -> minSalary == null
                ? cb.conjunction()
                : cb.and(cb.isNotNull(root.get("salaryMax")), cb.greaterThanOrEqualTo(root.get("salaryMax"), minSalary));
    }

    /** The mirror of {@link #hasMinSalary}: "within my budget", matched against {@code salaryMin}. */
    public static Specification<Job> hasMaxSalary(BigDecimal maxSalary) {
        return (root, query, cb) -> maxSalary == null
                ? cb.conjunction()
                : cb.and(cb.isNotNull(root.get("salaryMin")), cb.lessThanOrEqualTo(root.get("salaryMin"), maxSalary));
    }

    /**
     * Joins through {@code job_skills} to {@code skills}. {@code query.distinct(true)} is
     * set defensively even though filtering on exactly one skill name cannot itself produce
     * duplicate rows (a job has at most one {@code JobSkill} per distinct skill, by
     * {@code job_skills}' composite primary key) — cheap insurance against a future change
     * to this method (e.g. matching several skills at once) reintroducing row fan-out
     * silently.
     */
    public static Specification<Job> requiresSkill(String skillName) {
        return (root, query, cb) -> {
            if (skillName == null || skillName.isBlank()) {
                return cb.conjunction();
            }
            query.distinct(true);
            Join<Job, JobSkill> jobSkills = root.join("requiredSkills");
            Join<JobSkill, Skill> skill = jobSkills.join("skill");
            return cb.equal(skill.get("name"), Skill.normalize(skillName));
        };
    }
}
