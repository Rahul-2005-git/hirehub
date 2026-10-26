package com.hirehub.dto.application;

import com.hirehub.entity.enums.ApplicationStatus;

import java.time.Instant;

/**
 * @param fromStatus    {@code null} for the very first entry (the initial {@code APPLIED}
 *                       row), where there is no previous status
 * @param changedByName {@code null} if the user who made the change has since been deleted —
 *                       {@code application_status_history.changed_by_user_id} is
 *                       {@code ON DELETE SET NULL} by design (Phase 2A), so the trail
 *                       outlives the actor; only a name, never the id or email, is exposed
 *                       here
 */
public record ApplicationStatusHistoryResponse(
        ApplicationStatus fromStatus,
        ApplicationStatus toStatus,
        String changedByName,
        String note,
        Instant changedAt
) {
}
