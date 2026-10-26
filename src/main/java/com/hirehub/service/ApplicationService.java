package com.hirehub.service;

import com.hirehub.dto.application.ApplicationResponse;
import com.hirehub.dto.application.ApplicationStatusHistoryResponse;
import com.hirehub.dto.application.ApplyToJobRequest;
import com.hirehub.dto.application.UpdateApplicationStatusRequest;
import com.hirehub.dto.common.PageResponse;
import com.hirehub.dto.notification.NotificationDto;
import com.hirehub.entity.Application;
import com.hirehub.entity.CandidateProfile;
import com.hirehub.entity.Company;
import com.hirehub.entity.Job;
import com.hirehub.entity.RecruiterProfile;
import com.hirehub.entity.enums.ApplicationStatus;
import com.hirehub.entity.enums.JobStatus;
import com.hirehub.exception.DuplicateResourceException;
import com.hirehub.exception.InvalidStateException;
import com.hirehub.exception.ResourceNotFoundException;
import com.hirehub.mapper.ApplicationMapper;
import com.hirehub.repository.ApplicationRepository;
import com.hirehub.repository.CandidateProfileRepository;
import com.hirehub.repository.JobRepository;
import com.hirehub.repository.RecruiterProfileRepository;
import com.hirehub.security.HireHubUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * The application workflow: apply, withdraw, recruiter status changes, and read access.
 *
 * <p><b>Transaction boundaries</b> (DATABASE.md §7, documented in Phase 0):
 * {@link #apply} inserts the {@code applications} row and its first
 * {@code application_status_history} row in one commit, and {@link #updateStatus} /
 * {@link #withdraw} change the status and append a history row together. Both work because
 * {@link Application} owns its history with {@code CascadeType.ALL} and only appends to it
 * through {@code recordInitialStatus} / {@code changeStatus} — a status can never change
 * without its history entry riding along in the same flush, and a failure rolls back both.</p>
 *
 * <p>Ownership follows the same rules as the rest of the app: a candidate owns applications
 * whose {@code candidate} is their own profile; a recruiter owns applications on jobs
 * belonging to their <em>company</em> (company-wide, matching {@code JobService}); an admin
 * may view anything. Violations throw {@link AccessDeniedException}, giving the same 403 as
 * a role failure.</p>
 */
@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final JobRepository jobRepository;
    private final ApplicationMapper mapper;
    private final NotificationService notificationService;

    public ApplicationService(ApplicationRepository applicationRepository,
                               CandidateProfileRepository candidateProfileRepository,
                               RecruiterProfileRepository recruiterProfileRepository,
                               JobRepository jobRepository, ApplicationMapper mapper,
                               NotificationService notificationService) {
        this.applicationRepository = applicationRepository;
        this.candidateProfileRepository = candidateProfileRepository;
        this.recruiterProfileRepository = recruiterProfileRepository;
        this.jobRepository = jobRepository;
        this.mapper = mapper;
        this.notificationService = notificationService;
    }

    /**
     * The fourth appearance of the two-layer duplicate guard (emails, skills, company names,
     * now applications): the {@code existsBy...} check below gives the friendly message, and
     * {@code uq_applications_job_candidate} is what holds if two requests race — the loser's
     * insert fails at commit and {@code GlobalExceptionHandler} turns the resulting
     * {@code DataIntegrityViolationException} into the same 409.
     */
    @Transactional
    public ApplicationResponse apply(Long userId, Long jobId, ApplyToJobRequest request) {
        CandidateProfile candidate = loadCandidateProfile(userId);

        if (!candidate.isCompleteEnoughToApply()) {
            throw new InvalidStateException(
                    "Complete your profile (a headline and a resume link) before applying to jobs");
        }

        Job job = jobRepository.findWithDetailsById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));

        // A DRAFT job is invisible to candidates everywhere else (JobService.getById), so
        // applying to one must look identical to applying to a job that doesn't exist.
        if (job.getStatus() == JobStatus.DRAFT) {
            throw new ResourceNotFoundException("Job not found: " + jobId);
        }
        if (!job.acceptsApplications()) {
            throw new InvalidStateException("Job " + jobId + " is not accepting applications");
        }
        if (applicationRepository.existsByJobIdAndCandidateId(jobId, candidate.getId())) {
            throw new DuplicateResourceException("You have already applied to this job");
        }

        Application application = new Application(job, candidate,
                request == null ? null : blankToNull(request.coverLetter()));
        application.recordInitialStatus(candidate.getUser());
        application = applicationRepository.save(application);

        // Notify recruiter / job poster of the new incoming application
        if (job.getCreatedBy() != null && job.getCreatedBy().getId() != null) {
            String candidateName = (candidate.getUser() != null && candidate.getUser().getFullName() != null)
                    ? candidate.getUser().getFullName() : "A candidate";
            notificationService.sendToUser(
                    job.getCreatedBy().getId(),
                    NotificationDto.of(
                            "New Application Received",
                            candidateName + " applied for " + job.getTitle(),
                            "NEW_APPLICATION",
                            application.getId()
                    )
            );
        }

        return mapper.toResponse(application);
    }

    /** Candidate-only, and only for their own application. Terminal applications cannot be withdrawn. */
    @Transactional
    public ApplicationResponse withdraw(Long userId, Long applicationId) {
        CandidateProfile candidate = loadCandidateProfile(userId);
        Application application = loadApplication(applicationId);

        if (!application.getCandidate().getId().equals(candidate.getId())) {
            throw new AccessDeniedException("You do not have permission to withdraw this application");
        }
        if (!application.changeStatus(ApplicationStatus.WITHDRAWN, candidate.getUser(), null)) {
            throw new InvalidStateException(
                    "Application " + applicationId + " cannot be withdrawn while " + application.getStatus());
        }

        return mapper.toResponse(application);
    }

    /**
     * A recruiter moves an application through the pipeline. Two independent rules apply:
     * the status must be one a recruiter is allowed to set at all
     * ({@code isAssignableByRecruiter} — WITHDRAWN belongs to the candidate), and the
     * transition must be legal from the current status ({@code canTransitionTo}, enforced
     * inside {@code Application.changeStatus}). Both live on the {@code ApplicationStatus}
     * enum, so there is one place to read or change either rule.
     */
    @Transactional
    public ApplicationResponse updateStatus(Long userId, Long applicationId, UpdateApplicationStatusRequest request) {
        RecruiterProfile recruiter = loadRecruiterProfile(userId);
        Application application = loadApplication(applicationId);

        if (!ownsJobOf(recruiter, application)) {
            throw new AccessDeniedException("You do not have permission to manage this application");
        }
        if (!request.status().isAssignableByRecruiter()) {
            throw new InvalidStateException(
                    "Status " + request.status() + " cannot be set by a recruiter");
        }
        if (!application.changeStatus(request.status(), recruiter.getUser(), blankToNull(request.note()))) {
            throw new InvalidStateException("Application " + applicationId + " cannot move from "
                    + application.getStatus() + " to " + request.status());
        }

        // Real-time alert to candidate on status update
        if (application.getCandidate() != null && application.getCandidate().getUser() != null) {
            notificationService.sendToUser(
                    application.getCandidate().getUser().getId(),
                    NotificationDto.of(
                            "Application Status: " + request.status(),
                            "Your application for " + application.getJob().getTitle() + " has been updated to " + request.status(),
                            "STATUS_CHANGE",
                            application.getId()
                    )
            );
        }

        return mapper.toResponse(application);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> getMyApplications(Long userId, ApplicationStatus status, Pageable pageable) {
        CandidateProfile candidate = loadCandidateProfile(userId);
        Page<Application> page = status == null
                ? applicationRepository.findByCandidateId(candidate.getId(), pageable)
                : applicationRepository.findByCandidateIdAndStatus(candidate.getId(), status, pageable);
        return toPage(page);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> getJobApplications(Long userId, Long jobId, ApplicationStatus status,
                                                                  Pageable pageable) {
        RecruiterProfile recruiter = loadRecruiterProfile(userId);
        Job job = jobRepository.findWithDetailsById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found: " + jobId));

        if (!recruiter.hasCompany() || !sameCompany(recruiter.getCompany(), job.getCompany())) {
            throw new AccessDeniedException("You do not have permission to view this job's applications");
        }

        Page<Application> page = status == null
                ? applicationRepository.findByJobId(jobId, pageable)
                : applicationRepository.findByJobIdAndStatus(jobId, status, pageable);
        return toPage(page);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getById(HireHubUserDetails principal, Long applicationId) {
        Application application = loadApplication(applicationId);
        assertCanView(principal, application);
        return mapper.toResponse(application);
    }

    @Transactional(readOnly = true)
    public List<ApplicationStatusHistoryResponse> getHistory(HireHubUserDetails principal, Long applicationId) {
        Application application = loadApplication(applicationId);
        assertCanView(principal, application);
        return application.getStatusHistory().stream().map(mapper::toHistoryResponse).toList();
    }

    /** Candidate who owns it, recruiter whose company owns the job, or an admin. */
    private void assertCanView(HireHubUserDetails principal, Application application) {
        boolean admin = principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (admin) {
            return;
        }

        boolean isOwningCandidate = candidateProfileRepository.findByUserId(principal.getUserId())
                .map(c -> c.getId().equals(application.getCandidate().getId()))
                .orElse(false);
        boolean isOwningRecruiter = recruiterProfileRepository.findByUserId(principal.getUserId())
                .map(r -> ownsJobOf(r, application))
                .orElse(false);

        if (!isOwningCandidate && !isOwningRecruiter) {
            throw new AccessDeniedException("You do not have permission to view this application");
        }
    }

    private boolean ownsJobOf(RecruiterProfile recruiter, Application application) {
        return recruiter.hasCompany()
                && sameCompany(recruiter.getCompany(), application.getJob().getCompany());
    }

    private static boolean sameCompany(Company a, Company b) {
        if (a == null || b == null) {
            return false;
        }
        if (a == b) {
            return true;
        }
        return a.getId() != null && a.getId().equals(b.getId());
    }

    private Application loadApplication(Long applicationId) {
        return applicationRepository.findWithHistoryById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
    }

    private CandidateProfile loadCandidateProfile(Long userId) {
        return candidateProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate profile not found for the current user"));
    }

    private RecruiterProfile loadRecruiterProfile(Long userId) {
        return recruiterProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recruiter profile not found for the current user"));
    }

    private PageResponse<ApplicationResponse> toPage(Page<Application> page) {
        return PageResponse.of(page, page.getContent().stream().map(mapper::toResponse).toList());
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
