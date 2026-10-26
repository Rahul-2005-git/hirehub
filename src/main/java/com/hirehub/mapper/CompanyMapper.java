package com.hirehub.mapper;

import com.hirehub.dto.company.CompanyResponse;
import com.hirehub.dto.company.RecruiterProfileResponse;
import com.hirehub.entity.Company;
import com.hirehub.entity.RecruiterProfile;
import org.springframework.stereotype.Component;

/**
 * Kept as one mapper rather than two: {@code RecruiterProfileResponse} embeds a nested
 * {@code CompanyResponse}, so the two mappings are never used independently of each other in
 * practice — the same reasoning that kept skill mapping inside {@code CandidateProfileMapper}
 * instead of its own class.
 */
@Component
public class CompanyMapper {

    public CompanyResponse toResponse(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getWebsite(),
                company.getDescription(),
                company.getIndustry(),
                company.getCompanySize(),
                company.getLogoUrl(),
                company.getStatus()
        );
    }

    public RecruiterProfileResponse toResponse(RecruiterProfile recruiterProfile) {
        return new RecruiterProfileResponse(
                recruiterProfile.getId(),
                recruiterProfile.getUser().getFullName(),
                recruiterProfile.getUser().getEmail(),
                recruiterProfile.getDesignation(),
                recruiterProfile.hasCompany() ? toResponse(recruiterProfile.getCompany()) : null
        );
    }
}
