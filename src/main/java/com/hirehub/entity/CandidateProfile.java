package com.hirehub.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Candidate-specific data, one row per candidate {@link User}.
 *
 * <p>Unlike {@link Company#getName()}'s missing job collection, the skill collection <b>is</b>
 * mapped here. WHY the difference: a candidate has a handful of skills that are always
 * edited as a unit and always displayed together, so a cascading, orphan-removing collection
 * models the real aggregate. A company's jobs are an unbounded, independently paged list —
 * a different kind of relationship that deserves a repository, not a field.</p>
 *
 * <p>{@code expected_salary} is {@link BigDecimal}, never {@code double}: money must not be
 * stored in binary floating point, where 0.1 has no exact representation.</p>
 */
@Entity
@Table(name = "candidate_profiles")
public class CandidateProfile extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "headline", length = 150)
    private String headline;

    @Column(name = "summary", columnDefinition = "text")
    private String summary;

    @Column(name = "experience_years", nullable = false)
    private Integer experienceYears = 0;

    @Column(name = "current_location", length = 120)
    private String currentLocation;

    @Column(name = "expected_salary", precision = 12, scale = 2)
    private BigDecimal expectedSalary;

    @Column(name = "resume_url", length = 512)
    private String resumeUrl;

    @Column(name = "resume_file_name", length = 255)
    private String resumeFileName;

    @Column(name = "linkedin_url", length = 255)
    private String linkedinUrl;

    @Column(name = "github_url", length = 255)
    private String githubUrl;

    /**
     * Owned collection: saving the profile saves its skills, and removing one from the set
     * deletes the row ({@code orphanRemoval}). LAZY so that listing candidates does not
     * fetch every skill; {@code default_batch_fetch_size} in application.yml keeps the
     * follow-up fetch to one query for a whole page rather than one per candidate.
     */
    @OneToMany(mappedBy = "candidateProfile", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<CandidateSkill> skills = new LinkedHashSet<>();

    protected CandidateProfile() {
        // required by JPA
    }

    public CandidateProfile(User user) {
        this.user = user;
    }

    /**
     * Keeps both sides of the association in step. Letting callers write
     * {@code profile.getSkills().add(x)} without setting {@code x.candidateProfile} is the
     * classic source of "the row saved with a null FK" bugs.
     */
    public void addSkill(CandidateSkill skill) {
        skills.add(skill);
        skill.setCandidateProfile(this);
    }

    public void removeSkill(CandidateSkill skill) {
        skills.remove(skill);
        skill.setCandidateProfile(null);
    }

    /** A profile must be usable before the candidate is allowed to apply (Phase 10). */
    public boolean isCompleteEnoughToApply() {
        return headline != null && !headline.isBlank()
                && resumeUrl != null && !resumeUrl.isBlank();
    }

    public User getUser() {
        return user;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Integer getExperienceYears() {
        return experienceYears;
    }

    public void setExperienceYears(Integer experienceYears) {
        this.experienceYears = experienceYears;
    }

    public String getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(String currentLocation) {
        this.currentLocation = currentLocation;
    }

    public BigDecimal getExpectedSalary() {
        return expectedSalary;
    }

    public void setExpectedSalary(BigDecimal expectedSalary) {
        this.expectedSalary = expectedSalary;
    }

    public String getResumeUrl() {
        return resumeUrl;
    }

    public void setResumeUrl(String resumeUrl) {
        this.resumeUrl = resumeUrl;
    }

    public String getResumeFileName() {
        return resumeFileName;
    }

    public void setResumeFileName(String resumeFileName) {
        this.resumeFileName = resumeFileName;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public void setLinkedinUrl(String linkedinUrl) {
        this.linkedinUrl = linkedinUrl;
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public void setGithubUrl(String githubUrl) {
        this.githubUrl = githubUrl;
    }

    public Set<CandidateSkill> getSkills() {
        return skills;
    }

    @Override
    public String toString() {
        return "CandidateProfile{id=" + getId() + ", headline='" + headline + "'}";
    }
}
