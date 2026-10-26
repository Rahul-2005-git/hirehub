package com.hirehub.entity.enums;

/**
 * Seniority band a job targets. Declared from least to most senior; the ordering is
 * documentation only, since the value is persisted as a string, never as an ordinal.
 */
public enum ExperienceLevel {
    INTERNSHIP,
    ENTRY_LEVEL,
    MID_LEVEL,
    SENIOR_LEVEL,
    LEAD,
    EXECUTIVE
}
