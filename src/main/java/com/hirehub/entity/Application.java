package com.hirehub.entity;

import com.hirehub.entity.enums.ApplicationStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A candidate's application to a job — the many-to-many between the two, carrying state.
 *
 * <p>Does not extend {@link AuditableEntity} because the table names its creation column
 * {@code applied_at} rather than {@code created_at}; the auditing annotations are applied
 * directly instead of bending the shared superclass out of shape.</p>
 *
 * <p><b>The duplicate guard.</b> Uniqueness of {@code (job, candidate)} is enforced by
 * {@code uq_applications_job_candidate} in the database. A service-level check exists only
 * to produce a friendly message: under concurrency two requests can both read "not applied"
 * before either writes, and only the unique index rejects the second insert.</p>
 *
 * <p>{@code @Version} prevents two recruiters advancing the same application to different
 * stages and one silently winning.</p>
 */
@Entity
@Table(name = "applications")
@EntityListeners(AuditingEntityListener.class)
public class Application extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_profile_id", nullable = false)
    private CandidateProfile candidate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.APPLIED;

    @Column(name = "cover_letter", columnDefinition = "text")
    private String coverLetter;

    /**
     * Snapshot of the resume link as it was at application time. If the candidate later
     * replaces their resume, the recruiter still sees what they actually applied with.
     */
    @Column(name = "resume_url", length = 512)
    private String resumeUrl;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * The audit trail, owned by the application: cascading persists a new entry with its
     * parent inside the same transaction. There is no {@code orphanRemoval} and nothing
     * ever removes an entry — the trail is append-only by design.
     */
    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("changedAt ASC")
    private List<ApplicationStatusHistory> statusHistory = new ArrayList<>();

    protected Application() {
        // required by JPA
    }

    public Application(Job job, CandidateProfile candidate, String coverLetter) {
        this.job = job;
        this.candidate = candidate;
        this.coverLetter = coverLetter;
        this.resumeUrl = candidate.getResumeUrl();
    }

    /**
     * Records the initial APPLIED entry. Called by {@code ApplicationService} inside the
     * same transaction as the insert, so an application can never exist without history.
     */
    public void recordInitialStatus(User actor) {
        statusHistory.add(new ApplicationStatusHistory(this, null, ApplicationStatus.APPLIED, actor, null));
    }

    /**
     * Moves the application to {@code target} and appends a history entry.
     *
     * @return {@code false} when the transition is not legal, leaving the application
     *         untouched so the service can reply 422 instead of corrupting the pipeline.
     */
    public boolean changeStatus(ApplicationStatus target, User actor, String note) {
        if (!status.canTransitionTo(target)) {
            return false;
        }
        ApplicationStatus previous = this.status;
        this.status = target;
        statusHistory.add(new ApplicationStatusHistory(this, previous, target, actor, note));
        return true;
    }

    public boolean isActive() {
        return !status.isTerminal();
    }

    public Job getJob() {
        return job;
    }

    public CandidateProfile getCandidate() {
        return candidate;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public String getCoverLetter() {
        return coverLetter;
    }

    public String getResumeUrl() {
        return resumeUrl;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Unmodifiable: history is appended through {@link #changeStatus}, never edited. */
    public List<ApplicationStatusHistory> getStatusHistory() {
        return List.copyOf(statusHistory);
    }

    @Override
    public String toString() {
        return "Application{id=" + getId() + ", status=" + status + "}";
    }
}
