package com.hirehub.dto.auth;

import com.hirehub.entity.enums.RoleName;

/**
 * The public-facing shape of a {@code User} — deliberately excludes
 * {@code passwordHash} by construction, not by remembering to leave a field out. This is
 * the DTO-boundary rule from ARCHITECTURE.md §3 in its simplest form: an entity never
 * crosses the controller, so there is no field here to accidentally serialize.
 */
public record UserSummaryResponse(
        Long id,
        String email,
        String fullName,
        RoleName role
) {
}
