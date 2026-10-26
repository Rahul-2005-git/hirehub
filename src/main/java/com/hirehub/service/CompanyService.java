package com.hirehub.service;

import com.hirehub.dto.admin.ApprovalDecision;
import com.hirehub.dto.common.PageResponse;
import com.hirehub.dto.company.CompanyResponse;
import com.hirehub.dto.company.CreateCompanyRequest;
import com.hirehub.dto.company.UpdateCompanyRequest;
import com.hirehub.entity.Company;
import com.hirehub.entity.RecruiterProfile;
import com.hirehub.entity.enums.CompanyStatus;
import com.hirehub.exception.DuplicateResourceException;
import com.hirehub.exception.InvalidStateException;
import com.hirehub.exception.ResourceNotFoundException;
import com.hirehub.mapper.CompanyMapper;
import com.hirehub.repository.CompanyRepository;
import com.hirehub.repository.RecruiterProfileRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Company creation, editing, and the admin approval decision.
 *
 * <p><b>Ownership is checked here, not by {@code @PreAuthorize}.</b> "May this recruiter
 * edit company X" depends on which company their profile points to — data no annotation
 * expression can see without a database read first, so it belongs in the method body,
 * exactly as ARCHITECTURE.md §5 describes. The violation is signalled by throwing Spring
 * Security's own {@link AccessDeniedException} directly from the service. This is
 * deliberately the same exception type {@code @PreAuthorize} failures throw, so a role
 * failure and an ownership failure produce byte-for-byte the same 403 JSON shape — the
 * client should not be able to tell the two apart, and now there is only one code path that
 * could ever format that response differently by accident.</p>
 *
 * <p>The two failure paths are caught by two different pieces of machinery for the same
 * result: an {@code @PreAuthorize} failure never reaches a controller method at all — it is
 * thrown by the method-security interceptor and caught by
 * {@code ExceptionTranslationFilter}, which calls {@code JwtAccessDeniedHandler}. This
 * service's own {@code AccessDeniedException}, thrown from inside a normal method call, is
 * caught by {@code GlobalExceptionHandler.handleAccessDenied} instead, since by that point
 * request handling is already inside the {@code DispatcherServlet}, not still in the
 * security filter chain. Two mechanisms, one outcome — worth knowing the difference between
 * for a "how does 403 handling work here" question.</p>
 */
@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final CompanyMapper mapper;

    public CompanyService(CompanyRepository companyRepository,
                           RecruiterProfileRepository recruiterProfileRepository, CompanyMapper mapper) {
        this.companyRepository = companyRepository;
        this.recruiterProfileRepository = recruiterProfileRepository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public CompanyResponse getById(Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + companyId));
        return mapper.toResponse(company);
    }

    /**
     * A recruiter may belong to at most one company at a time, and
     * {@code recruiter_profiles.company_id} enforces that structurally: setting it here
     * simply overwrites whatever was there, so a second call could not silently create a
     * second membership even if the guard below were skipped. The guard exists purely to
     * give a clear error instead of silently detaching the recruiter from their real employer.
     */
    @Transactional
    public CompanyResponse createCompany(Long userId, CreateCompanyRequest request) {
        RecruiterProfile recruiterProfile = recruiterProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recruiter profile not found for the current user"));

        if (recruiterProfile.hasCompany()) {
            throw new DuplicateResourceException(
                    "You already belong to a company. Contact an administrator to change it.");
        }
        if (companyRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("A company with this name already exists");
        }

        Company company = new Company(request.name().trim());
        applyEditableFields(company, request.website(), request.description(),
                request.industry(), request.companySize());
        companyRepository.save(company);

        recruiterProfile.setCompany(company);

        return mapper.toResponse(company);
    }

    @Transactional
    public CompanyResponse updateCompany(Long userId, Long companyId, UpdateCompanyRequest request) {
        RecruiterProfile recruiterProfile = recruiterProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recruiter profile not found for the current user"));

        if (!recruiterProfile.hasCompany() || !sameId(recruiterProfile.getCompany().getId(), companyId)) {
            throw new AccessDeniedException("You do not have permission to edit this company");
        }

        // Already the managed entity this recruiter owns - no second fetch by companyId.
        Company company = recruiterProfile.getCompany();

        if (!company.getName().equalsIgnoreCase(request.name())
                && companyRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException("A company with this name already exists");
        }

        company.setName(request.name().trim());
        applyEditableFields(company, request.website(), request.description(),
                request.industry(), request.companySize());

        return mapper.toResponse(company);
    }

    @Transactional(readOnly = true)
    public PageResponse<CompanyResponse> listByStatus(CompanyStatus status, Pageable pageable) {
        var page = companyRepository.findByStatus(status, pageable);
        return PageResponse.of(page, page.getContent().stream().map(mapper::toResponse).toList());
    }

    /**
     * The transaction boundary DATABASE.md §7 already documented for this operation in
     * Phase 0: flips the company's status in one commit. It does not yet activate any
     * pending jobs, because jobs don't exist until Phase 8 — the boundary is drawn where
     * the documentation already said it would be, ready for that phase to extend it rather
     * than needing a new transaction wrapped around this one later.
     */
    @Transactional
    public CompanyResponse decide(Long companyId, ApprovalDecision decision) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + companyId));

        if (company.getStatus() != CompanyStatus.PENDING) {
            throw new InvalidStateException(
                    "Company " + companyId + " has already been decided (status: " + company.getStatus() + ")");
        }

        company.setStatus(decision.toCompanyStatus());
        return mapper.toResponse(company);
    }

    private void applyEditableFields(Company company, String website, String description,
                                      String industry, String companySize) {
        company.setWebsite(blankToNull(website));
        company.setDescription(blankToNull(description));
        company.setIndustry(blankToNull(industry));
        company.setCompanySize(blankToNull(companySize));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static boolean sameId(Long a, Long b) {
        return a != null && a.equals(b);
    }
}
