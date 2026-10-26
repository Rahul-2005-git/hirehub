package com.hirehub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Join entity between {@link Job} and {@link Skill}, carrying whether the skill is
 * mandatory or merely desirable. Same reasoning as {@link CandidateSkill}: the relationship
 * has data, so it is modelled explicitly.
 */
@Entity
@Table(name = "job_skills")
public class JobSkill {

    @EmbeddedId
    private JobSkillId id = new JobSkillId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("jobId")
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("skillId")
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory = true;

    protected JobSkill() {
        // required by JPA
    }

    public JobSkill(Skill skill, boolean mandatory) {
        this.skill = skill;
        this.mandatory = mandatory;
    }

    public JobSkillId getId() {
        return id;
    }

    public Job getJob() {
        return job;
    }

    void setJob(Job job) {
        // package-private: maintained by Job.addSkill/removeSkill
        this.job = job;
    }

    public Skill getSkill() {
        return skill;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JobSkill that)) {
            return false;
        }
        return Objects.equals(skill, that.skill) && Objects.equals(job, that.job);
    }

    @Override
    public int hashCode() {
        return Objects.hash(skill);
    }

    @Override
    public String toString() {
        return "JobSkill{skill=" + skill + ", mandatory=" + mandatory + "}";
    }
}
