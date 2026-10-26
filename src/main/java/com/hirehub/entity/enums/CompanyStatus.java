package com.hirehub.entity.enums;

/**
 * Moderation state of a company. A company must be APPROVED before its jobs can be
 * published, which stops unvetted organisations from posting.
 */
public enum CompanyStatus {
    PENDING,
    APPROVED,
    REJECTED
}
