package com.hirehub.dto.company;

/**
 * @param company {@code null} when the recruiter has registered but not yet created or
 *                joined a company — a valid, expected state (see
 *                {@code RecruiterProfile.hasCompany()}), not an error condition.
 */
public record RecruiterProfileResponse(
        Long id,
        String fullName,
        String email,
        String designation,
        CompanyResponse company
) {
}
