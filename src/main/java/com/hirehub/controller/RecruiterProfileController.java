package com.hirehub.controller;

import com.hirehub.dto.common.ApiResponse;
import com.hirehub.dto.common.PageResponse;
import com.hirehub.dto.company.RecruiterProfileResponse;
import com.hirehub.dto.company.UpdateRecruiterProfileRequest;
import com.hirehub.dto.job.JobResponse;
import com.hirehub.security.HireHubUserDetails;
import com.hirehub.service.JobService;
import com.hirehub.service.RecruiterProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recruiters")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Recruiter Profile", description = "Recruiter self-service profile")
public class RecruiterProfileController {

    private final RecruiterProfileService recruiterProfileService;
    private final JobService jobService;

    public RecruiterProfileController(RecruiterProfileService recruiterProfileService, JobService jobService) {
        this.recruiterProfileService = recruiterProfileService;
        this.jobService = jobService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('RECRUITER')")
    @Operation(summary = "The current recruiter's profile, including their company if any")
    public ResponseEntity<ApiResponse<RecruiterProfileResponse>> getMyProfile(
            @AuthenticationPrincipal HireHubUserDetails principal) {
        var response = recruiterProfileService.getMyProfile(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.of("Recruiter profile", response));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('RECRUITER')")
    @Operation(summary = "Update the current recruiter's profile",
            description = "Company membership is not editable here - see POST /api/companies.")
    public ResponseEntity<ApiResponse<RecruiterProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal HireHubUserDetails principal,
            @Valid @RequestBody UpdateRecruiterProfileRequest request) {
        var response = recruiterProfileService.updateMyProfile(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.of("Profile updated successfully", response));
    }

    @GetMapping("/me/jobs")
    @PreAuthorize("hasRole('RECRUITER')")
    @Operation(summary = "Jobs created by the current recruiter",
            description = "Personal authorship, not the whole company's postings - see JobService's ownership-model Javadoc for the distinction.")
    public ApiResponse<PageResponse<JobResponse>> getMyJobs(
            @AuthenticationPrincipal HireHubUserDetails principal, Pageable pageable) {
        return ApiResponse.of("Your jobs", jobService.myJobs(principal.getUserId(), pageable));
    }
}
