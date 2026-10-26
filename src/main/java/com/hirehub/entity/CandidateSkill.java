package com.hirehub.entity;

import com.hirehub.entity.enums.ProficiencyLevel;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Join entity between {@link CandidateProfile} and {@link Skill}.
 *
 * <p>WHY an entity rather than {@code @ManyToMany}: the relationship carries data
 * ({@code proficiency}, {@code yearsOfExperience}), which {@code @ManyToMany} cannot
 * express at all. It also avoids that annotation's habit of deleting and re-inserting the
 * whole join table when one element changes.</p>
 *
 * <p>{@code @MapsId} ties each association to one half of the composite key, so the FK
 * columns and the PK columns are the same columns — no duplication, and the key is
 * populated automatically when the parents are persisted.</p>
 */
@Entity
@Table(name = "candidate_skills")
public class CandidateSkill {

    @EmbeddedId
    private CandidateSkillId id = new CandidateSkillId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("candidateProfileId")
    @JoinColumn(name = "candidate_profile_id", nullable = false)
    private CandidateProfile candidateProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("skillId")
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(name = "proficiency", nullable = false, length = 20)
    private ProficiencyLevel proficiency = ProficiencyLevel.INTERMEDIATE;

    @Column(name = "years_of_experience", nullable = false)
    private Integer yearsOfExperience = 0;

    protected CandidateSkill() {
        // required by JPA
    }

    public CandidateSkill(Skill skill, ProficiencyLevel proficiency, Integer yearsOfExperience) {
        this.skill = skill;
        this.proficiency = proficiency;
        this.yearsOfExperience = yearsOfExperience;
    }

    public CandidateSkillId getId() {
        return id;
    }

    public CandidateProfile getCandidateProfile() {
        return candidateProfile;
    }

    void setCandidateProfile(CandidateProfile candidateProfile) {
        // package-private: set only through CandidateProfile.addSkill/removeSkill,
        // so the two sides can never disagree.
        this.candidateProfile = candidateProfile;
    }

    public Skill getSkill() {
        return skill;
    }

    public ProficiencyLevel getProficiency() {
        return proficiency;
    }

    public void setProficiency(ProficiencyLevel proficiency) {
        this.proficiency = proficiency;
    }

    public Integer getYearsOfExperience() {
        return yearsOfExperience;
    }

    public void setYearsOfExperience(Integer yearsOfExperience) {
        this.yearsOfExperience = yearsOfExperience;
    }

    // Identity is the (candidate, skill) pair. Before persist the ids are still null, so
    // equality falls back to the associated entities - which is what a Set needs while the
    // profile is being built in memory.
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CandidateSkill that)) {
            return false;
        }
        return Objects.equals(skill, that.skill)
                && Objects.equals(candidateProfile, that.candidateProfile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(skill);
    }

    @Override
    public String toString() {
        return "CandidateSkill{skill=" + skill + ", proficiency=" + proficiency + "}";
    }
}
