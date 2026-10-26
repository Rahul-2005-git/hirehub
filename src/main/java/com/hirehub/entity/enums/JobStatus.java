package com.hirehub.entity.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle of a job posting.
 *
 * <p>The legal transitions live on the enum rather than scattered through
 * {@code JobService}. WHY: the rule "a CLOSED job cannot be re-published" is a property of
 * the domain, so keeping it next to the states means there is exactly one place to read,
 * change and test it. The service asks {@link #canTransitionTo(JobStatus)} instead of
 * growing a nested if/else that drifts out of sync.</p>
 *
 * <p>ALTERNATIVE: a Spring State Machine. Rejected — a dependency and a configuration DSL
 * for four states would be over-engineering.</p>
 */
public enum JobStatus {

    /** Created but not visible to candidates. Only the owning recruiter can see it. */
    DRAFT,

    /** Live and accepting applications. The only status the public job feed returns. */
    PUBLISHED,

    /** Deliberately closed by the recruiter. Existing applications remain readable. */
    CLOSED,

    /** Passed its application deadline. Closed automatically rather than by a person. */
    EXPIRED;

    private static final Map<JobStatus, Set<JobStatus>> ALLOWED_TRANSITIONS = Map.of(
            DRAFT, EnumSet.of(PUBLISHED, CLOSED),
            PUBLISHED, EnumSet.of(CLOSED, EXPIRED),
            EXPIRED, EnumSet.of(CLOSED),
            CLOSED, EnumSet.noneOf(JobStatus.class)
    );

    public boolean canTransitionTo(JobStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    /** True when the job accepts new applications. */
    public boolean isOpenForApplications() {
        return this == PUBLISHED;
    }

    /** True when no further status change is possible. */
    public boolean isTerminal() {
        return ALLOWED_TRANSITIONS.get(this).isEmpty();
    }
}
