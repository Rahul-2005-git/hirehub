package com.hirehub.entity.enums;

/**
 * Account state. Admins disable accounts rather than deleting them, so applications and
 * audit history stay intact.
 */
public enum UserStatus {
    ACTIVE,
    DISABLED
}
