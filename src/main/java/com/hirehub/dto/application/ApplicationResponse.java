package com.hirehub.dto.application;

import com.hirehub.dto.auth.UserSummaryResponse;
import com.hirehub.entity.enums.ApplicationStatus;

import java.time.Instant;
import java.util.List;

/**
 * Carries both the job and the candidate on every response, along with candidate skills
 * for intelligent applicant ranking and skill-match scoring in the recruiter ATS.
 */
public record ApplicationResponse(
        Long id,
        JobSummaryResponse job,
        UserSummaryResponse candidate,
        ApplicationStatus status,
        String coverLetter,
        String resumeUrl,
        Instant appliedAt,
        Instant updatedAt,
        Long candidateProfileId,
        List<String> candidateSkills
) {

    /**
     * Backward-compatible constructor for existing tests and fixtures.
     */
    public ApplicationResponse(Long id, JobSummaryResponse job, UserSummaryResponse candidate,
                               ApplicationStatus status, String coverLetter, String resumeUrl,
                               Instant appliedAt, Instant updatedAt) {
        this(id, job, candidate, status, coverLetter, resumeUrl, appliedAt, updatedAt, null, List.of());
    }
}
