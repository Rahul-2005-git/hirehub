package com.hirehub.service;

import com.hirehub.dto.common.PageResponse;
import com.hirehub.dto.job.CreateJobRequest;
import com.hirehub.dto.job.JobResponse;
import com.hirehub.dto.job.JobSearchCriteria;
import com.hirehub.dto.job.JobSkillRequest;
import com.hirehub.dto.job.UpdateJobRequest;
import com.hirehub.entity.Company;
import com.hirehub.entity.Job;
import com.hirehub.entity.JobSkill;
import com.hirehub.entity.RecruiterProfile;
import com.hirehub.entity.Skill;
import com.hirehub.entity.enums.JobStatus;
import com.hirehub.exception.InvalidStateException;
import com.hirehub.exception.ResourceNotFoundException;
import com.hirehub.mapper.JobMapper;
import com.hirehub.repository.JobRepository;
import com.hirehub.repository.RecruiterProfileRepository;
import com.hirehub.security.HireHubUserDetails;
import com.hirehub.specification.JobSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Job CRUD and the {@link JobStatus} lifecycle.
 *
 * <p><b>Ownership model: company-level, not creator-level.</b> Any recruiter belonging to a
 * job's company may update, publish, close or delete it — not only the one recruiter who
 * happened to click "create". This matches how a real hiring team works (a colleague should
 * be able to fix a typo in a teammate's posting) and is why every ownership check below
 * compares {@code recruiterProfile.getCompany().getId()} against {@code job.getCompany().getId()},
 * never against {@code job.getCreatedBy()}. The column {@code jobs.created_by} still records
 * who actually authored it — that is audit information, not an access boundary.</p>
 *
 * <p>The same {@link AccessDeniedException}-from-a-service-method pattern as
 * {@code CompanyService} is used here for exactly the same reason: a role check alone
 * ({@code @PreAuthorize("hasRole('RECRUITER')")}) cannot see which company a job belongs to,
 * so the finer-grained check has to happen after the data is loaded.</p>
 */
@Service
public class JobService {

    private final JobRepository jobRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final SkillService skillService;
    private final JobMapper mapper;

    public JobService(JobRepository jobRepository, RecruiterProfileRepository recruiterProfileRepository,
                       SkillService skillService, JobMapper mapper) {
        this.jobRepository = jobRepository;
        this.recruiterProfileRepository = recruiterProfileRepository;
        this.skillService = skillService;
        this.mapper = mapper;
    }

    @Transactional
    public JobResponse createJob(Long userId, CreateJobRequest request) {
        RecruiterProfile recruiterProfile = loadRecruiterProfile(userId);

        if (!recruiterProfile.hasCompany()) {
            throw new InvalidStateException(
                    "You must belong to a company before creating a job - see POST /api/companies");
        }

        Job job = new Job(recruiterProfile.getCompany(), recruiterProfile, request.title().trim(),
                request.description(), request.location().trim(), request.employmentType(),
                request.experienceLevel());
        applyEditableFields(job, request.salaryMin(), request.salaryMax(), request.currency(),
                request.openings(), request.applicationDeadline());
        jobRepository.save(job);

        replaceSkills(job, request.skills());

        return mapper.toResponse(job);
    }

    /**
     * Public unless the job is {@code DRAFT}, in which case only a recruiter from the
     * owning company or an admin may see it. {@code viewer} is {@code null} for anonymous
     * callers — {@code JobController} passes whatever
     * {@code @AuthenticationPrincipal HireHubUserDetails} resolves to, which is null when
     * there is no token at all, exactly like any other optional-authentication read.
     *
     * <p>A non-owning caller gets {@link ResourceNotFoundException}, not
     * {@link AccessDeniedException}: revealing "this id exists but you can't see it" would
     * leak the existence of an unpublished job to someone with no right to know about it at
     * all, which is worse than a 404 that looks identical to "there is nothing here".</p>
     */
    @Transactional(readOnly = true)
    public JobResponse getById(Long jobId, HireHubUserDetails viewer) {
        Job job = jobRepository.findWithDetailsById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));

        if (job.getStatus() == JobStatus.DRAFT && !canViewDraft(job, viewer)) {
            throw new ResourceNotFoundException("Job not found: " + jobId);
        }

        return mapper.toResponse(job);
    }

    private boolean canViewDraft(Job job, HireHubUserDetails viewer) {
        if (viewer == null) {
            return false;
        }
        if (viewer.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return true;
        }
        return recruiterProfileRepository.findByUserId(viewer.getUserId())
                .map(rp -> rp.hasCompany() && sameCompany(rp.getCompany(), job.getCompany()))
                .orElse(false);
    }

    /**
     * Public sortable properties for {@code GET /api/jobs}. Deliberately an allow-list, not
     * a block-list: {@code Pageable}'s sort binds straight from a query string
     * ({@code ?sort=someField,desc}), so an unvalidated sort would let a client order by any
     * mapped property at all — including ones with no supporting index (an expensive sort
     * on an unindexed column) or ones that shouldn't be exposed as a sort key even if
     * harmless performance-wise. Anything not in this set is silently dropped rather than
     * rejected with an error: a client sorting on a typo'd or unsupported field still gets a
     * usable, correctly-filtered result instead of a hard failure over what is, after all,
     * only an ordering preference.
     */
    private static final Set<String> SORTABLE_PROPERTIES = Set.of("createdAt", "title", "salaryMin", "salaryMax");

    /**
     * The Specification-based search ARCHITECTURE.md §7 described in Phase 0 and
     * {@code JobRepository} was wired for (via {@code JpaSpecificationExecutor}) back in
     * Phase 3. Always includes {@code hasStatus(PUBLISHED)} — this is the public feed, so an
     * unauthenticated or non-owning search can never surface a {@code DRAFT} job through a
     * filter combination, independent of the separate visibility check in {@link #getById}.
     */
    @Transactional(readOnly = true)
    public PageResponse<JobResponse> search(JobSearchCriteria criteria, Pageable pageable) {
        Specification<Job> spec = Specification
                .where(JobSpecifications.hasStatus(JobStatus.PUBLISHED))
                .and(JobSpecifications.hasKeyword(criteria.keyword()))
                .and(JobSpecifications.hasLocation(criteria.location()))
                .and(JobSpecifications.hasEmploymentType(criteria.employmentType()))
                .and(JobSpecifications.hasExperienceLevel(criteria.experienceLevel()))
                .and(JobSpecifications.hasMinSalary(criteria.minSalary()))
                .and(JobSpecifications.hasMaxSalary(criteria.maxSalary()))
                .and(JobSpecifications.requiresSkill(criteria.skill()));

        Page<Job> page = jobRepository.findAll(spec, withDefaultSort(pageable));
        return PageResponse.of(page, page.getContent().stream().map(mapper::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<JobResponse> myJobs(Long userId, Pageable pageable) {
        RecruiterProfile recruiterProfile = loadRecruiterProfile(userId);
        Page<Job> page = jobRepository.findByCreatedById(recruiterProfile.getId(), withDefaultSort(pageable));
        return PageResponse.of(page, page.getContent().stream().map(mapper::toResponse).toList());
    }

    /**
     * Editing is allowed while {@code DRAFT} or {@code PUBLISHED} — a live posting can still
     * have a typo fixed — but not once {@code CLOSED} or {@code EXPIRED}: at that point the
     * posting is a historical record that applications may already reference, and silently
     * rewriting it after the fact would undermine that record.
     */
    @Transactional
    public JobResponse updateJob(Long userId, Long jobId, UpdateJobRequest request) {
        Job job = loadOwnedJob(userId, jobId);

        if (job.getStatus() != JobStatus.DRAFT && job.getStatus() != JobStatus.PUBLISHED) {
            throw new InvalidStateException(
                    "Job " + jobId + " cannot be edited while " + job.getStatus());
        }

        job.setTitle(request.title().trim());
        job.setDescription(request.description());
        job.setLocation(request.location().trim());
        job.setEmploymentType(request.employmentType());
        job.setExperienceLevel(request.experienceLevel());
        applyEditableFields(job, request.salaryMin(), request.salaryMax(), request.currency(),
                request.openings(), request.applicationDeadline());

        replaceSkills(job, request.skills());

        return mapper.toResponse(job);
    }

    /**
     * Publishing additionally requires the owning company to be {@code APPROVED}
     * (DATABASE.md §1) — a recruiter can prepare and edit a draft job before that approval
     * comes through, but it cannot go live until it does.
     */
    @Transactional
    public JobResponse publishJob(Long userId, Long jobId) {
        Job job = loadOwnedJob(userId, jobId);

        if (!job.getCompany().isApproved()) {
            throw new InvalidStateException(
                    "Company must be approved by an administrator before its jobs can be published");
        }
        if (!job.publish()) {
            throw new InvalidStateException("Job " + jobId + " cannot be published while " + job.getStatus());
        }

        return mapper.toResponse(job);
    }

    @Transactional
    public JobResponse closeJob(Long userId, Long jobId) {
        Job job = loadOwnedJob(userId, jobId);

        if (!job.close()) {
            throw new InvalidStateException("Job " + jobId + " cannot be closed while " + job.getStatus());
        }

        return mapper.toResponse(job);
    }

    /** Only a {@code DRAFT} job may be deleted — anything published may already have applications. */
    @Transactional
    public void deleteJob(Long userId, Long jobId) {
        Job job = loadOwnedJob(userId, jobId);

        if (!job.isDeletable()) {
            throw new InvalidStateException(
                    "Job " + jobId + " cannot be deleted while " + job.getStatus() + " - close it instead");
        }

        jobRepository.deleteById(job.getId());
    }

    private Job loadOwnedJob(Long userId, Long jobId) {
        RecruiterProfile recruiterProfile = loadRecruiterProfile(userId);
        Job job = jobRepository.findWithDetailsById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));

        if (!recruiterProfile.hasCompany() ||
                !sameCompany(recruiterProfile.getCompany(), job.getCompany())) {
            throw new AccessDeniedException("You do not have permission to manage this job");
        }
        return job;
    }

    private RecruiterProfile loadRecruiterProfile(Long userId) {
        return recruiterProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recruiter profile not found for the current user"));
    }

    private boolean sameCompany(Company a, Company b) {
        if (a == null || b == null) {
            return false;
        }
        if (a == b) {
            return true;
        }
        return a.getId() != null && a.getId().equals(b.getId());
    }

    /**
     * Full replace, the same contract {@code UpdateJobRequest}'s Javadoc documents: every
     * call clears the current skill set and rebuilds it from the given list, rather than
     * diffing and issuing individual adds/removes. {@code null} is treated as "no change
     * requested" (skips replacement entirely) so that a client updating unrelated fields via
     * {@code UpdateJobRequest} isn't forced to always resend the full skill list.
     */
    private void replaceSkills(Job job, List<JobSkillRequest> skillRequests) {
        if (skillRequests == null) {
            return;
        }

        Set<JobSkill> current = new LinkedHashSet<>(job.getRequiredSkills());
        current.forEach(job::removeSkill);

        for (JobSkillRequest skillRequest : skillRequests) {
            Skill skill = skillService.findOrCreateSkill(skillRequest.skillName());
            job.addSkill(new JobSkill(skill, Boolean.TRUE.equals(skillRequest.mandatory())));
        }
    }

    private void applyEditableFields(Job job, BigDecimal salaryMin, BigDecimal salaryMax,
                                      String currency, Integer openings, LocalDate applicationDeadline) {
        job.setSalaryMin(salaryMin);
        job.setSalaryMax(salaryMax);
        if (currency != null && !currency.isBlank()) {
            job.setCurrency(currency);
        }
        if (openings != null) {
            job.setOpenings(openings);
        }
        job.setApplicationDeadline(applicationDeadline);
    }

    /**
     * Applies the {@link #SORTABLE_PROPERTIES} allow-list, then defaults to newest-first if
     * nothing sortable remains (either the caller specified no sort at all, or specified
     * only disallowed properties that were just stripped out).
     */
    private Pageable withDefaultSort(Pageable pageable) {
        Sort allowedSort = Sort.by(pageable.getSort().stream()
                .filter(order -> SORTABLE_PROPERTIES.contains(order.getProperty()))
                .collect(Collectors.toList()));

        Sort effectiveSort = allowedSort.isSorted() ? allowedSort : Sort.by(Sort.Direction.DESC, "createdAt");

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), effectiveSort);
    }

}
