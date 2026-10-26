package com.hirehub.dto.admin;

import jakarta.validation.constraints.NotNull;

/**
 * No {@code note} field: there is nowhere to persist one yet (no admin-action-audit table
 * exists). Accepting and silently discarding one would repeat the exact mistake corrected
 * in Phase 4A ({@code JWT_REFRESH_EXPIRATION_MS} — documenting or accepting input for
 * functionality that doesn't actually do anything). A note can be added back once there is
 * a real table behind it.
 */
public record CompanyApprovalRequest(

        @NotNull(message = "Decision is required")
        ApprovalDecision decision
) {
}
