package com.hirehub.dto.auth;

import com.hirehub.entity.enums.RoleName;

/**
 * The roles a person may request for themselves at {@code POST /api/auth/register}.
 *
 * <p><b>Security note:</b> {@link RoleName} has three values, but this enum has two.
 * {@code ADMIN} is deliberately absent — not filtered out in code after parsing, but
 * structurally impossible to deserialize from a client's JSON body. A field typed as
 * {@code RoleName} would let a request body simply say {@code "role": "ADMIN"}, and the
 * only thing stopping privilege escalation would be a service-layer {@code if}. Making the
 * type itself unable to represent {@code ADMIN} removes that failure mode entirely: there
 * is no code path to forget to add the check to. Admin accounts are provisioned directly
 * (a future seed script or an existing admin's user-management endpoint), never through
 * self-registration.</p>
 */
public enum RegistrableRole {
    CANDIDATE,
    RECRUITER;

    public RoleName toRoleName() {
        return RoleName.valueOf(name());
    }
}
