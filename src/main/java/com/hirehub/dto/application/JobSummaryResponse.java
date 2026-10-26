package com.hirehub.dto.application;

import com.hirehub.entity.enums.JobStatus;

/**
 * Deliberately not the full {@code JobResponse}: an application list row needs to say which
 * job it is for, not carry the job's whole description, skill list and nested company. A
 * page of twenty applications embedding twenty full job payloads would be twenty times the
 * data for information the row never displays.
 */
public record JobSummaryResponse(
        Long id,
        String title,
        String companyName,
        JobStatus status
) {
}
