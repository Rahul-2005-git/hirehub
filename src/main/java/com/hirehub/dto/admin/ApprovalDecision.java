package com.hirehub.dto.admin;

import com.hirehub.entity.enums.CompanyStatus;

/**
 * The outcomes an admin can set for a company under review. Deliberately not
 * {@link CompanyStatus} itself: that enum also has {@code PENDING}, which is a state a
 * company starts in, never one an admin "decides" to set it back to through this endpoint.
 * The same structural-guard reasoning as {@code RegistrableRole} (Phase 4B) — making an
 * invalid choice impossible to express is stronger than trusting every call site to reject
 * it correctly.
 */
public enum ApprovalDecision {
    APPROVED,
    REJECTED;

    public CompanyStatus toCompanyStatus() {
        return CompanyStatus.valueOf(name());
    }
}
