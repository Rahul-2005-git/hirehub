package com.hirehub.repository;

import com.hirehub.entity.Application;
import com.hirehub.entity.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    /**
     * The service-level half of the duplicate-application guard (the other half is
     * {@code uq_applications_job_candidate} itself — see ARCHITECTURE.md §6). Checking here
     * first lets {@code ApplicationService} return a clear "you already applied" message
     * for the common case; the constraint is what actually holds under a race.
     */
    boolean existsByJobIdAndCandidateId(Long jobId, Long candidateId);

    Optional<Application> findByJobIdAndCandidateId(Long jobId, Long candidateId);

    /**
     * Candidate dashboard: "my applications", optionally filtered by status.
     *
     * <p>{@code @EntityGraph} here is not optional polish: {@code ApplicationMapper} reads
     * {@code job.company.name} and {@code candidate.user.fullName/email} for every row it
     * maps. Without this, a page of twenty applications would fire the two follow-up queries
     * per row separately — 40 extra queries for one page — the textbook N+1 this project's
     * own brief asks about directly. One query in, one query out, regardless of page size.
     */
    @EntityGraph(attributePaths = {"job", "job.company", "candidate", "candidate.user"})
    Page<Application> findByCandidateIdAndStatus(Long candidateId, ApplicationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"job", "job.company", "candidate", "candidate.user"})
    Page<Application> findByCandidateId(Long candidateId, Pageable pageable);

    /** Recruiter pipeline view for one job, optionally filtered by status. Same N+1 reasoning as above. */
    @EntityGraph(attributePaths = {"job", "job.company", "candidate", "candidate.user"})
    Page<Application> findByJobIdAndStatus(Long jobId, ApplicationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"job", "job.company", "candidate", "candidate.user"})
    Page<Application> findByJobId(Long jobId, Pageable pageable);

    /**
     * Application detail view: the application plus everything {@code ApplicationMapper}
     * and the status-history timeline need, in one round trip.
     */
    @EntityGraph(attributePaths = {"job", "job.company", "candidate", "candidate.user",
            "statusHistory", "statusHistory.changedBy"})
    Optional<Application> findWithHistoryById(Long id);

    long countByJobIdAndStatus(Long jobId, ApplicationStatus status);
}
