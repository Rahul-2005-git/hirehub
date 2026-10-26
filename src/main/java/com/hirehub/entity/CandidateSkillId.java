package com.hirehub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key of {@link CandidateSkill}, mirroring
 * {@code pk_candidate_skills (candidate_profile_id, skill_id)}.
 *
 * <p>An {@code @EmbeddedId} class must be {@link Serializable} and must implement
 * {@code equals}/{@code hashCode} — Hibernate uses them to key the persistence context.
 * ALTERNATIVE: a surrogate id plus a unique constraint on the pair. Rejected: the pair
 * <em>is</em> the identity, and a surrogate would add a column that nothing queries.</p>
 */
@Embeddable
public class CandidateSkillId implements Serializable {

    @Column(name = "candidate_profile_id")
    private Long candidateProfileId;

    @Column(name = "skill_id")
    private Long skillId;

    protected CandidateSkillId() {
        // required by JPA
    }

    public CandidateSkillId(Long candidateProfileId, Long skillId) {
        this.candidateProfileId = candidateProfileId;
        this.skillId = skillId;
    }

    public Long getCandidateProfileId() {
        return candidateProfileId;
    }

    public Long getSkillId() {
        return skillId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CandidateSkillId that)) {
            return false;
        }
        return Objects.equals(candidateProfileId, that.candidateProfileId)
                && Objects.equals(skillId, that.skillId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(candidateProfileId, skillId);
    }
}
