package com.hirehub.controller;

import com.hirehub.dto.common.ApiResponse;
import com.hirehub.dto.common.PageResponse;
import com.hirehub.dto.job.CreateJobRequest;
import com.hirehub.dto.job.JobResponse;
import com.hirehub.dto.job.JobSearchCriteria;
import com.hirehub.dto.job.UpdateJobRequest;
import com.hirehub.security.HireHubUserDetails;
import com.hirehub.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /} and {@code GET /{id}} are public, the same pattern
 * {@code CompanyController} already established for browsing without an account. Every
 * write goes through {@code @PreAuthorize("hasRole('RECRUITER')")} plus a company-level
 * ownership check inside {@code JobService} — see that class's Javadoc for why ownership
 * can't live in the annotation.
 *
 * <p>{@code viewer} on {@link #getById} is deliberately nullable: {@code @AuthenticationPrincipal}
 * resolves to {@code null} for an anonymous request rather than throwing (Spring Security's
 * default when the principal's runtime type doesn't match), which is exactly the "may or may
 * not be logged in" signal {@code JobService.getById} needs to decide whether to reveal a
 * {@code DRAFT} job.</p>
 */
@RestController
@RequestMapping("/api/jobs")
@Tag(name = "Jobs", description = "Job postings: public browsing, recruiter management, and the status lifecycle")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    @PreAuthorize("hasRole('RECRUITER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a job", description = "Starts as DRAFT. Requires the recruiter to belong to a company.")
    public ResponseEntity<ApiResponse<JobResponse>> create(
            @AuthenticationPrincipal HireHubUserDetails principal,
            @Valid @RequestBody CreateJobRequest request) {
        var response = jobService.createJob(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of("Job created successfully", response));
    }

    @GetMapping
    @Operation(summary = "Search published jobs",
            description = "All filters are optional and combine with AND. Returns PUBLISHED jobs only, newest first by default. "
                    + "Sorting is restricted to an allow-list (createdAt, title, salaryMin, salaryMax) - any other sort field is ignored.")
    public ApiResponse<PageResponse<JobResponse>> list(
            @ModelAttribute JobSearchCriteria criteria,
            @Parameter(description = "page, size, sort - e.g. sort=salaryMax,desc") Pageable pageable) {
        return ApiResponse.of("Jobs", jobService.search(criteria, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "View a job's details",
            description = "Public for published/closed/expired jobs. A DRAFT job is visible only to a recruiter from the owning company, or an admin.")
    public ResponseEntity<ApiResponse<JobResponse>> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal HireHubUserDetails viewer) {
        var response = jobService.getById(id, viewer);
        return ResponseEntity.ok(ApiResponse.of("Job details", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('RECRUITER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update a job",
            description = "Allowed while DRAFT or PUBLISHED. Any recruiter from the owning company may edit it.")
    public ResponseEntity<ApiResponse<JobResponse>> update(
            @AuthenticationPrincipal HireHubUserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateJobRequest request) {
        var response = jobService.updateJob(principal.getUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.of("Job updated successfully", response));
    }

    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasRole('RECRUITER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Publish a draft job", description = "Requires the owning company to be APPROVED.")
    public ResponseEntity<ApiResponse<JobResponse>> publish(
            @AuthenticationPrincipal HireHubUserDetails principal, @PathVariable Long id) {
        var response = jobService.publishJob(principal.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.of("Job published successfully", response));
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasRole('RECRUITER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Close a job")
    public ResponseEntity<ApiResponse<JobResponse>> close(
            @AuthenticationPrincipal HireHubUserDetails principal, @PathVariable Long id) {
        var response = jobService.closeJob(principal.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.of("Job closed successfully", response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('RECRUITER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete a job", description = "Only a DRAFT job may be deleted.")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal HireHubUserDetails principal, @PathVariable Long id) {
        jobService.deleteJob(principal.getUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
