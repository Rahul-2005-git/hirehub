package com.hirehub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Composite primary key of {@link JobSkill}: {@code (job_id, skill_id)}. */
@Embeddable
public class JobSkillId implements Serializable {

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "skill_id")
    private Long skillId;

    protected JobSkillId() {
        // required by JPA
    }

    public JobSkillId(Long jobId, Long skillId) {
        this.jobId = jobId;
        this.skillId = skillId;
    }

    public Long getJobId() {
        return jobId;
    }

    public Long getSkillId() {
        return skillId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JobSkillId that)) {
            return false;
        }
        return Objects.equals(jobId, that.jobId) && Objects.equals(skillId, that.skillId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(jobId, skillId);
    }
}
