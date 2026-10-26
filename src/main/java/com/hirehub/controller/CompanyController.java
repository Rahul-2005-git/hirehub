package com.hirehub.controller;

import com.hirehub.dto.common.ApiResponse;
import com.hirehub.dto.company.CompanyResponse;
import com.hirehub.dto.company.CreateCompanyRequest;
import com.hirehub.dto.company.UpdateCompanyRequest;
import com.hirehub.security.HireHubUserDetails;
import com.hirehub.service.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /{id}} is public - see {@code SecurityConfig}'s method-scoped
 * {@code permitAll()} for that path - so this is the first controller in the app with a mix
 * of public and protected methods side by side. The ownership check for {@code PUT} lives
 * in {@link CompanyService}, not here: see that class's Javadoc for why.
 */
@RestController
@RequestMapping("/api/companies")
@Tag(name = "Companies", description = "Company profiles: creation, public viewing, and editing by the owning recruiter")
public class CompanyController {

    private final CompanyService companyService;

    public CompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @PostMapping
    @PreAuthorize("hasRole('RECRUITER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a company and join it",
            description = "The creating recruiter is attached to the new company. New companies start PENDING and need admin approval before their jobs can be published.")
    public ResponseEntity<ApiResponse<CompanyResponse>> create(
            @AuthenticationPrincipal HireHubUserDetails principal,
            @Valid @RequestBody CreateCompanyRequest request) {
        var response = companyService.createCompany(principal.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of("Company created successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "View a company's public profile")
    public ResponseEntity<ApiResponse<CompanyResponse>> getById(@PathVariable Long id) {
        var response = companyService.getById(id);
        return ResponseEntity.ok(ApiResponse.of("Company profile", response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('RECRUITER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update a company",
            description = "Only a recruiter belonging to this exact company may edit it.")
    public ResponseEntity<ApiResponse<CompanyResponse>> update(
            @AuthenticationPrincipal HireHubUserDetails principal,
            @PathVariable Long id,
            @Valid @RequestBody UpdateCompanyRequest request) {
        var response = companyService.updateCompany(principal.getUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.of("Company updated successfully", response));
    }
}
