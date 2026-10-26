package com.hirehub.dto.application;

import com.hirehub.entity.enums.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Uses {@link ApplicationStatus} directly, not a restricted subset enum like
 * {@code RegistrableRole} or {@code ApprovalDecision} elsewhere in the app. Those exist
 * because nothing else in the domain already encodes which values are permitted; here,
 * {@code ApplicationStatus.isAssignableByRecruiter()} (Phase 2B) already is that single
 * source of truth, and a second enum restating the same rule would be two places to keep in
 * sync instead of one. {@code ApplicationService.updateStatus} checks it, plus
 * {@code canTransitionTo}, at runtime.
 *
 * <p>Unlike {@code CompanyApprovalRequest} (where a {@code note} was dropped for having
 * nowhere to be stored), {@code note} here does have real storage:
 * {@code application_status_history.note}, part of the schema since Phase 2A.</p>
 */
public record UpdateApplicationStatusRequest(

        @NotNull(message = "Status is required")
        ApplicationStatus status,

        @Size(max = 2000, message = "Note must be at most 2000 characters")
        String note
) {
}
