package com.hirehub.dto.application;

import jakarta.validation.constraints.Size;

/**
 * {@code coverLetter} is the only field a candidate provides — everything else about an
 * application (which job, which candidate, the initial status) is derived from the URL path
 * and the authenticated principal, never from the request body. This is the same
 * structural-ownership pattern used throughout the app: there is no field here a candidate
 * could tamper with to apply as, or to, someone else.
 */
public record ApplyToJobRequest(

        @Size(max = 4000, message = "Cover letter must be at most 4000 characters")
        String coverLetter
) {
}
