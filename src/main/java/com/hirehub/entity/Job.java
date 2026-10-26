package com.hirehub.entity;

import com.hirehub.entity.enums.EmploymentType;
import com.hirehub.entity.enums.ExperienceLevel;
import com.hirehub.entity.enums.JobStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A job posting.
 *
 * <p><b>Optimistic locking.</b> {@code @Version} maps the {@code version} column added in
 * V1. Every update carries {@code WHERE id = ? AND version = ?}; if a second recruiter
 * saved first, zero rows match and Hibernate throws
 * {@code OptimisticLockingFailureException}, which Phase 12 turns into a 409. WHY this over
 * pessimistic {@code SELECT ... FOR UPDATE}: simultaneous edits of the same job are rare,
 * and row locks would make the common case — reading job listings — slower to protect
 * against the uncommon one. The trade-off is that the loser must retry.</p>
 *
 * <p>Status changes go through {@link #publish()} / {@link #close()} rather than a public
 * {@code setStatus}, so the transition rules on {@link JobStatus} cannot be bypassed and
 * {@code publishedAt} / {@code closedAt} can never disagree with {@code status}.</p>
 */
@Entity
@Table(name = "jobs")
public class Job extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private RecruiterProfile createdBy;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "location", nullable = false, length = 120)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 30)
    private EmploymentType employmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", nullable = false, length = 30)
    private ExperienceLevel experienceLevel;

    @Column(name = "salary_min", precision = 12, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 12, scale = 2)
    private BigDecimal salaryMax;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "openings", nullable = false)
    private Integer openings = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status = JobStatus.DRAFT;

    /** Calendar date, not an instant: a deadline is "end of the 30th", not a timestamp. */
    @Column(name = "application_deadline")
    private LocalDate applicationDeadline;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "job", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<JobSkill> requiredSkills = new LinkedHashSet<>();

    protected Job() {
        // required by JPA
    }

    public Job(Company company, RecruiterProfile createdBy, String title, String description,
               String location, EmploymentType employmentType, ExperienceLevel experienceLevel) {
        this.company = company;
        this.createdBy = createdBy;
        this.title = title;
        this.description = description;
        this.location = location;
        this.employmentType = employmentType;
        this.experienceLevel = experienceLevel;
    }

    // --- behaviour -----------------------------------------------------------------

    /**
     * @return {@code false} if the current status does not allow publishing, leaving the
     *         job untouched. The service turns that into a 409 rather than silently
     *         succeeding.
     */
    public boolean publish() {
        if (!status.canTransitionTo(JobStatus.PUBLISHED)) {
            return false;
        }
        this.status = JobStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        return true;
    }

    public boolean close() {
        if (!status.canTransitionTo(JobStatus.CLOSED)) {
            return false;
        }
        this.status = JobStatus.CLOSED;
        this.closedAt = Instant.now();
        return true;
    }

    public boolean expire() {
        if (!status.canTransitionTo(JobStatus.EXPIRED)) {
            return false;
        }
        this.status = JobStatus.EXPIRED;
        this.closedAt = Instant.now();
        return true;
    }

    /** True only when the job is published and its deadline has not passed. */
    public boolean acceptsApplications() {
        return status.isOpenForApplications() && !isPastDeadline();
    }

    public boolean isPastDeadline() {
        return applicationDeadline != null && applicationDeadline.isBefore(LocalDate.now());
    }

    /** Only a draft may be deleted; published jobs may already have applications. */
    public boolean isDeletable() {
        return status == JobStatus.DRAFT;
    }

    public void addSkill(JobSkill jobSkill) {
        requiredSkills.add(jobSkill);
        jobSkill.setJob(this);
    }

    public void removeSkill(JobSkill jobSkill) {
        requiredSkills.remove(jobSkill);
        jobSkill.setJob(null);
    }

    // --- accessors -----------------------------------------------------------------

    public Company getCompany() {
        return company;
    }

    public RecruiterProfile getCreatedBy() {
        return createdBy;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(EmploymentType employmentType) {
        this.employmentType = employmentType;
    }

    public ExperienceLevel getExperienceLevel() {
        return experienceLevel;
    }

    public void setExperienceLevel(ExperienceLevel experienceLevel) {
        this.experienceLevel = experienceLevel;
    }

    public BigDecimal getSalaryMin() {
        return salaryMin;
    }

    public void setSalaryMin(BigDecimal salaryMin) {
        this.salaryMin = salaryMin;
    }

    public BigDecimal getSalaryMax() {
        return salaryMax;
    }

    public void setSalaryMax(BigDecimal salaryMax) {
        this.salaryMax = salaryMax;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Integer getOpenings() {
        return openings;
    }

    public void setOpenings(Integer openings) {
        this.openings = openings;
    }

    public JobStatus getStatus() {
        return status;
    }

    public LocalDate getApplicationDeadline() {
        return applicationDeadline;
    }

    public void setApplicationDeadline(LocalDate applicationDeadline) {
        this.applicationDeadline = applicationDeadline;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Long getVersion() {
        return version;
    }

    public Set<JobSkill> getRequiredSkills() {
        return requiredSkills;
    }

    @Override
    public String toString() {
        return "Job{id=" + getId() + ", title='" + title + "', status=" + status + "}";
    }
}
