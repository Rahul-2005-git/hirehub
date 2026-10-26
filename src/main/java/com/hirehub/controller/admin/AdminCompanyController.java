package com.hirehub.controller.admin;

import com.hirehub.dto.admin.CompanyApprovalRequest;
import com.hirehub.dto.common.ApiResponse;
import com.hirehub.dto.common.PageResponse;
import com.hirehub.dto.company.CompanyResponse;
import com.hirehub.entity.enums.CompanyStatus;
import com.hirehub.service.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * No {@code @PreAuthorize} anywhere in this class. {@code SecurityConfig}'s
 * {@code requestMatchers("/api/admin/**").hasRole("ADMIN")} already covers the entire
 * namespace this controller lives under - repeating the same check per method here would
 * be redundant, and worse, a maintainer could reasonably assume a future method without the
 * annotation was left open on purpose. One gate, at the URL level, for the whole namespace,
 * is the coarse half of the two-layer design actually being used the way it was intended.
 */
@RestController
@RequestMapping("/api/admin/companies")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin - Companies", description = "Company moderation queue")
public class AdminCompanyController {

    private final CompanyService companyService;

    public AdminCompanyController(CompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    @Operation(summary = "List companies, optionally filtered by status",
            description = "e.g. ?status=PENDING for the moderation queue")
    public ApiResponse<PageResponse<CompanyResponse>> list(
            @Parameter(description = "Defaults to PENDING - the moderation queue is the common case")
            @RequestParam(defaultValue = "PENDING") CompanyStatus status,
            Pageable pageable) {
        return ApiResponse.of("Companies", companyService.listByStatus(status, pageable));
    }

    @PatchMapping("/{id}/approval")
    @Operation(summary = "Approve or reject a pending company")
    public ApiResponse<CompanyResponse> decide(@PathVariable Long id,
                                                @Valid @RequestBody CompanyApprovalRequest request) {
        var response = companyService.decide(id, request.decision());
        return ApiResponse.of("Company " + request.decision().name().toLowerCase() + " successfully", response);
    }
}
