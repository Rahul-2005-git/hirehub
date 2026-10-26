package com.hirehub.controller;

import com.hirehub.dto.candidate.AddCandidateSkillRequest;
import com.hirehub.dto.candidate.CandidateProfileResponse;
import com.hirehub.dto.candidate.CandidateSkillResponse;
import com.hirehub.dto.candidate.UpdateCandidateProfileRequest;
import com.hirehub.dto.common.ApiResponse;
import com.hirehub.security.HireHubUserDetails;
import com.hirehub.service.CandidateProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The first controller in the app to use {@code @PreAuthorize} — see
 * {@code SecurityConfig}'s Javadoc for why role checks live here, at the method, rather than
 * as URL patterns.
 *
 * <p>Every {@code /me} endpoint resolves the profile from {@code principal.getUserId()},
 * never from a path variable — see {@code CandidateProfileService}'s Javadoc for why that
 * makes an ownership check unnecessary for those endpoints, structurally, rather than
 * something to remember to add.</p>
 */
@RestController
@RequestMapping("/api/candidates")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Candidate Profile", description = "Candidate self-service profile and skills")
public class CandidateProfileController {

    private final CandidateProfileService candidateProfileService;

    public CandidateProfileController(CandidateProfileService candidateProfileService) {
        this.candidateProfileService = candidateProfileService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "The current candidate's full profile, including skills")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> getMyProfile(
            @AuthenticationPrincipal HireHubUserDetails principal) {
        var response = candidateProfileService.getMyProfile(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.of("Candidate profile", response));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Replace the current candidate's editable profile fields")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal HireHubUserDetails principal,
            @Valid @RequestBody UpdateCandidateProfileRequest request) {
        var response = candidateProfileService.updateMyProfile(principal.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.of("Profile updated successfully", response));
    }

    @GetMapping("/me/skills")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "The current candidate's skills")
    public ResponseEntity<ApiResponse<List<CandidateSkillResponse>>> getMySkills(
            @AuthenticationPrincipal HireHubUserDetails principal) {
        var response = candidateProfileService.getMySkills(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.of("Candidate skills", response));
    }

    @PostMapping("/me/skills")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Add a skill to the current candidate's profile",
            description = "Finds the shared skill by name, creating it if it doesn't already exist.")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> addSkill(
            @AuthenticationPrincipal HireHubUserDetails principal,
            @Valid @RequestBody AddCandidateSkillRequest request) {
        var response = candidateProfileService.addSkill(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of("Skill added", response));
    }

    @DeleteMapping("/me/skills/{skillId}")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Remove a skill from the current candidate's profile")
    public ResponseEntity<Void> removeSkill(
            @AuthenticationPrincipal HireHubUserDetails principal,
            @PathVariable Long skillId) {
        candidateProfileService.removeSkill(principal.getUserId(), skillId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/me/resume", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Upload candidate PDF resume", description = "Accepts multipart/form-data with field name 'file'. Must be a PDF document.")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> uploadResume(
            @AuthenticationPrincipal HireHubUserDetails principal,
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        var response = candidateProfileService.uploadResume(principal.getUserId(), file);
        return ResponseEntity.ok(ApiResponse.of("Resume uploaded successfully", response));
    }

    @DeleteMapping("/me/resume")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Delete candidate's uploaded resume")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> deleteResume(
            @AuthenticationPrincipal HireHubUserDetails principal) {
        var response = candidateProfileService.deleteResume(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.of("Resume deleted successfully", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('RECRUITER') or hasRole('ADMIN')")
    @Operation(summary = "View a candidate's profile by id",
            description = "Available to recruiters and admins. Candidates use /me for their own profile.")
    public ResponseEntity<ApiResponse<CandidateProfileResponse>> getById(@PathVariable Long id) {
        var response = candidateProfileService.getById(id);
        return ResponseEntity.ok(ApiResponse.of("Candidate profile", response));
    }
}
