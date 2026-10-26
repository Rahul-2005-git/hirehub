package com.hirehub.service;

import com.hirehub.dto.company.RecruiterProfileResponse;
import com.hirehub.dto.company.UpdateRecruiterProfileRequest;
import com.hirehub.entity.RecruiterProfile;
import com.hirehub.exception.ResourceNotFoundException;
import com.hirehub.mapper.CompanyMapper;
import com.hirehub.repository.RecruiterProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deliberately does not let a recruiter change their own company through this service —
 * that only ever happens via {@link CompanyService#createCompany}. A separate
 * "set my company id to X" method here would let a recruiter attach themselves to any
 * company by id with no verification at all, which is a far more direct problem than
 * anything an ownership check could fix after the fact.
 */
@Service
public class RecruiterProfileService {

    private final RecruiterProfileRepository recruiterProfileRepository;
    private final CompanyMapper mapper;

    public RecruiterProfileService(RecruiterProfileRepository recruiterProfileRepository, CompanyMapper mapper) {
        this.recruiterProfileRepository = recruiterProfileRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public RecruiterProfileResponse getMyProfile(Long userId) {
        return mapper.toResponse(loadByUserId(userId));
    }

    @Transactional
    public RecruiterProfileResponse updateMyProfile(Long userId, UpdateRecruiterProfileRequest request) {
        RecruiterProfile profile = loadByUserId(userId);
        profile.setDesignation(blankToNull(request.designation()));
        return mapper.toResponse(profile);
    }

    private RecruiterProfile loadByUserId(Long userId) {
        return recruiterProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recruiter profile not found for the current user"));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
