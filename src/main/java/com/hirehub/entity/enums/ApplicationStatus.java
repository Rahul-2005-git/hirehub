package com.hirehub.entity.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Stage of a candidate's application — the recruitment pipeline.
 *
 * <pre>
 *   APPLIED → UNDER_REVIEW → SHORTLISTED → INTERVIEW → SELECTED
 *      │            │             │            │
 *      └────────────┴─────────────┴────────────┴──→ REJECTED   (recruiter, any stage)
 *      └────────────┴─────────────┴────────────┴──→ WITHDRAWN  (candidate only)
 * </pre>
 *
 * <p>Encoding the transitions here means an invalid move such as
 * {@code REJECTED → SHORTLISTED} is rejected by one shared rule rather than by whichever
 * service method happens to remember to check. Every accepted transition is also appended
 * to {@code application_status_history}, so the pipeline is auditable.</p>
 */
public enum ApplicationStatus {

    /** Set automatically when the candidate applies. The initial state. */
    APPLIED,

    /** A recruiter has picked the application up. */
    UNDER_REVIEW,

    /** Passed screening. */
    SHORTLISTED,

    /** Interview stage. */
    INTERVIEW,

    /** Offer stage — terminal, positive outcome. */
    SELECTED,

    /** Terminal, negative outcome. Reachable from any active stage. */
    REJECTED,

    /** Terminal. Only the candidate may move an application here. */
    WITHDRAWN;

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED_TRANSITIONS = Map.of(
            APPLIED, EnumSet.of(UNDER_REVIEW, REJECTED, WITHDRAWN),
            UNDER_REVIEW, EnumSet.of(SHORTLISTED, REJECTED, WITHDRAWN),
            SHORTLISTED, EnumSet.of(INTERVIEW, REJECTED, WITHDRAWN),
            INTERVIEW, EnumSet.of(SELECTED, REJECTED, WITHDRAWN),
            SELECTED, EnumSet.noneOf(ApplicationStatus.class),
            REJECTED, EnumSet.noneOf(ApplicationStatus.class),
            WITHDRAWN, EnumSet.noneOf(ApplicationStatus.class)
    );

    /** Statuses a recruiter is allowed to set. WITHDRAWN belongs to the candidate alone. */
    private static final Set<ApplicationStatus> RECRUITER_ASSIGNABLE =
            EnumSet.of(UNDER_REVIEW, SHORTLISTED, INTERVIEW, SELECTED, REJECTED);

    public boolean canTransitionTo(ApplicationStatus target) {
        return ALLOWED_TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return ALLOWED_TRANSITIONS.get(this).isEmpty();
    }

    public boolean isAssignableByRecruiter() {
        return RECRUITER_ASSIGNABLE.contains(this);
    }
}
