package com.hirehub.entity.enums;

/**
 * The three mutually exclusive roles in HireHub.
 *
 * <p>Persisted as its {@code name()} string in {@code roles.name}, guarded by the
 * {@code ck_roles_name} CHECK constraint. Rows are created by migration V2, so this enum
 * and the database can never disagree about which roles exist.</p>
 */
public enum RoleName {
    ADMIN,
    RECRUITER,
    CANDIDATE
}
