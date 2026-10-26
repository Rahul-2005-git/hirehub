package com.hirehub.dto.candidate;

import java.math.BigDecimal;
import java.util.List;

public record CandidateProfileResponse(
        Long id,
        String fullName,
        String email,
        String headline,
        String summary,
        Integer experienceYears,
        String currentLocation,
        BigDecimal expectedSalary,
        String resumeUrl,
        String resumeFileName,
        String linkedinUrl,
        String githubUrl,
        List<CandidateSkillResponse> skills
) {
}
