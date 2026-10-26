package com.hirehub.controller;

import com.hirehub.dto.application.ApplicationResponse;
import com.hirehub.dto.application.ApplicationStatusHistoryResponse;
import com.hirehub.dto.application.ApplyToJobRequest;
import com.hirehub.dto.application.UpdateApplicationStatusRequest;
import com.hirehub.dto.common.ApiResponse;
import com.hirehub.dto.common.PageResponse;
import com.hirehub.entity.enums.ApplicationStatus;
import com.hirehub.security.HireHubUserDetails;
import com.hirehub.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Mapped at {@code /api} rather than a single resource prefix because the application
 * endpoints span three URL families that mirror how each actor thinks about them:
 * {@code /jobs/{id}/applications} (a job's applicants), {@code /candidates/me/applications}
 * (my history) and {@code /applications/{id}} (one application, whoever is looking).
 *
 * <p>{@code getById} and {@code getHistory} carry no {@code @PreAuthorize}: every
 * authenticated user is a candidate, recruiter or admin, so a role annotation would admit
 * exactly the same set as {@code anyRequest().authenticated()}. The real decision — is this
 * <em>your</em> application, or one on your company's job — needs the loaded entity and is
 * made in {@code ApplicationService}.</p>
 */
@RestController
@RequestMapping("/api")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Applications", description = "Applying to jobs, tracking status, and recruiter pipeline management")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/jobs/{jobId}/applications")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Apply to a job",
            description = "Requires a complete profile (headline + resume link) and a job that is PUBLISHED and not past its deadline. A candidate can apply to a given job only once.")
    public ResponseEntity<ApiResponse<ApplicationResponse>> apply(
            @AuthenticationPrincipal HireHubUserDetails principal,
            @PathVariable Long jobId,
            @Valid @RequestBody(required = false) ApplyToJobRequest request) {
        var response = applicationService.apply(principal.getUserId(), jobId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of("Application submitted successfully", response));
    }

    @GetMapping("/candidates/me/applications")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "My applications", description = "Optionally filtered by status, e.g. ?status=SHORTLISTED")
    public ApiResponse<PageResponse<ApplicationResponse>> myApplications(
            @AuthenticationPrincipal HireHubUserDetails principal,
            @RequestParam(required = false) ApplicationStatus status,
            Pageable pageable) {
        return ApiResponse.of("Your applications",
                applicationService.getMyApplications(principal.getUserId(), status, pageable));
    }

    @GetMapping("/jobs/{jobId}/applications")
    @PreAuthorize("hasRole('RECRUITER')")
    @Operation(summary = "Applicants for a job",
            description = "Any recruiter from the job's company. Optionally filtered by status.")
    public ApiResponse<PageResponse<ApplicationResponse>> jobApplications(
            @AuthenticationPrincipal HireHubUserDetails principal,
            @PathVariable Long jobId,
            @RequestParam(required = false) ApplicationStatus status,
            Pageable pageable) {
        return ApiResponse.of("Applications",
                applicationService.getJobApplications(principal.getUserId(), jobId, status, pageable));
    }

    @GetMapping("/applications/{id}")
    @Operation(summary = "View an application",
            description = "The candidate who submitted it, a recruiter from the job's company, or an admin.")
    public ApiResponse<ApplicationResponse> getById(
            @AuthenticationPrincipal HireHubUserDetails principal, @PathVariable Long id) {
        return ApiResponse.of("Application", applicationService.getById(principal, id));
    }

    @PatchMapping("/applications/{id}/withdraw")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Withdraw my application",
            description = "Only the submitting candidate, and only while the application is still active.")
    public ApiResponse<ApplicationResponse> withdraw(
            @AuthenticationPrincipal HireHubUserDetails principal, @PathVariable Long id) {
        return ApiResponse.of("Application withdrawn", applicationService.withdraw(principal.getUserId(), id));
    }

    @PatchMapping("/applications/{id}/status")
    @PreAuthorize("hasRole('RECRUITER')")
    @Operation(summary = "Move an application through the pipeline",
            description = "APPLIED → UNDER_REVIEW → SHORTLISTED → INTERVIEW → SELECTED, with REJECTED reachable from any active stage. WITHDRAWN cannot be set by a recruiter.")
    public ApiResponse<ApplicationResponse> updateStatus(
            @AuthenticationPrincipal HireHubUserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateApplicationStatusRequest request) {
        return ApiResponse.of("Application status updated",
                applicationService.updateStatus(principal.getUserId(), id, request));
    }

    @GetMapping("/applications/{id}/history")
    @Operation(summary = "An application's full status history",
            description = "Append-only audit trail, oldest first. Same visibility as viewing the application itself.")
    public ApiResponse<List<ApplicationStatusHistoryResponse>> history(
            @AuthenticationPrincipal HireHubUserDetails principal, @PathVariable Long id) {
        return ApiResponse.of("Application history", applicationService.getHistory(principal, id));
    }
}
