package com.hirehub.entity;

import com.hirehub.entity.enums.ApplicationStatus;
import com.hirehub.entity.enums.EmploymentType;
import com.hirehub.entity.enums.ExperienceLevel;
import com.hirehub.entity.enums.JobStatus;
import com.hirehub.entity.enums.ProficiencyLevel;
import com.hirehub.entity.enums.RoleName;
import com.hirehub.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persists a complete graph — role → user → profile → skills → company → job → application
 * → status history — against a real PostgreSQL instance.
 *
 * <p>This is the test that proves the Phase 2B mappings agree with the Phase 2A migrations.
 * Hibernate's {@code ddl-auto: validate} already fails the context at startup on a column
 * mismatch; this goes further and exercises the composite keys, the cascades, the auditing
 * timestamps and the duplicate-application constraint.</p>
 */
@Transactional
class EntityMappingTest extends AbstractIntegrationTest {

    @Autowired
    private EntityManager em;

    private Role role(RoleName name) {
        return em.createQuery("select r from Role r where r.name = :n", Role.class)
                .setParameter("n", name)
                .getSingleResult();
    }

    private User newUser(RoleName roleName) {
        String email = UUID.randomUUID() + "@hirehub.test";
        User user = new User(email, "$2a$10$notarealhashnotarealhashnotarealhash", "Test User", role(roleName));
        em.persist(user);
        return user;
    }

    private CandidateProfile newCandidate() {
        CandidateProfile profile = new CandidateProfile(newUser(RoleName.CANDIDATE));
        profile.setHeadline("Backend Engineer");
        profile.setResumeUrl("https://files.example.test/resume.pdf");
        profile.setExperienceYears(3);
        profile.setExpectedSalary(new BigDecimal("1200000.00"));
        em.persist(profile);
        return profile;
    }

    private Job newPublishedJob() {
        Company company = new Company("Acme " + UUID.randomUUID());
        em.persist(company);

        RecruiterProfile recruiter = new RecruiterProfile(newUser(RoleName.RECRUITER));
        recruiter.setCompany(company);
        em.persist(recruiter);

        Job job = new Job(company, recruiter, "Senior Java Engineer",
                "Build and run HireHub.", "Pune",
                EmploymentType.FULL_TIME, ExperienceLevel.SENIOR_LEVEL);
        job.setSalaryMin(new BigDecimal("1500000.00"));
        job.setSalaryMax(new BigDecimal("2500000.00"));
        job.setApplicationDeadline(LocalDate.now().plusDays(30));
        job.publish();
        em.persist(job);
        return job;
    }

    @Test
    @DisplayName("reference roles from migration V2 are mapped and loadable")
    void rolesAreMapped() {
        assertThat(role(RoleName.CANDIDATE).getId()).isNotNull();
        assertThat(role(RoleName.ADMIN).getName()).isEqualTo(RoleName.ADMIN);
    }

    @Test
    @DisplayName("auditing populates createdAt and updatedAt on persist")
    void auditingTimestampsArePopulated() {
        User user = newUser(RoleName.CANDIDATE);
        em.flush();

        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("candidate skills persist through the owning profile with their composite key")
    void candidateSkillsCascadeFromProfile() {
        CandidateProfile profile = newCandidate();
        Skill java = em.createQuery("select s from Skill s where s.name = :n", Skill.class)
                .setParameter("n", "java")
                .getSingleResult();

        profile.addSkill(new CandidateSkill(java, ProficiencyLevel.ADVANCED, 4));
        em.flush();
        em.clear();

        CandidateProfile reloaded = em.find(CandidateProfile.class, profile.getId());
        assertThat(reloaded.getSkills()).hasSize(1);
        CandidateSkill persisted = reloaded.getSkills().iterator().next();
        assertThat(persisted.getId().getSkillId()).isEqualTo(java.getId());
        assertThat(persisted.getId().getCandidateProfileId()).isEqualTo(profile.getId());
        assertThat(persisted.getProficiency()).isEqualTo(ProficiencyLevel.ADVANCED);
    }

    @Test
    @DisplayName("publishing a job sets the status and the publishedAt timestamp")
    void publishingAJobIsPersisted() {
        Job job = newPublishedJob();
        em.flush();
        em.clear();

        Job reloaded = em.find(Job.class, job.getId());
        assertThat(reloaded.getStatus()).isEqualTo(JobStatus.PUBLISHED);
        assertThat(reloaded.getPublishedAt()).isNotNull();
        assertThat(reloaded.getVersion()).isNotNull();
        assertThat(reloaded.acceptsApplications()).isTrue();
    }

    @Test
    @DisplayName("an application cascades its initial status-history entry")
    void applicationPersistsWithInitialHistory() {
        CandidateProfile candidate = newCandidate();
        Job job = newPublishedJob();

        Application application = new Application(job, candidate, "I would like to apply.");
        application.recordInitialStatus(candidate.getUser());
        em.persist(application);
        em.flush();
        em.clear();

        Application reloaded = em.find(Application.class, application.getId());
        assertThat(reloaded.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(reloaded.getAppliedAt()).isNotNull();
        assertThat(reloaded.getStatusHistory()).hasSize(1);
        assertThat(reloaded.getStatusHistory().getFirst().getFromStatus()).isNull();
        assertThat(reloaded.getStatusHistory().getFirst().getToStatus())
                .isEqualTo(ApplicationStatus.APPLIED);
        // The resume link was snapshotted at application time.
        assertThat(reloaded.getResumeUrl()).isEqualTo(candidate.getResumeUrl());
    }

    @Test
    @DisplayName("each accepted status change appends one history entry")
    void statusChangesAppendHistory() {
        CandidateProfile candidate = newCandidate();
        Job job = newPublishedJob();
        User recruiterUser = job.getCreatedBy().getUser();

        Application application = new Application(job, candidate, null);
        application.recordInitialStatus(candidate.getUser());
        em.persist(application);

        assertThat(application.changeStatus(ApplicationStatus.UNDER_REVIEW, recruiterUser, "Screening")).isTrue();
        assertThat(application.changeStatus(ApplicationStatus.SHORTLISTED, recruiterUser, null)).isTrue();
        // Illegal jump: SHORTLISTED cannot go straight to SELECTED.
        assertThat(application.changeStatus(ApplicationStatus.SELECTED, recruiterUser, null)).isFalse();

        em.flush();
        em.clear();

        Application reloaded = em.find(Application.class, application.getId());
        assertThat(reloaded.getStatus()).isEqualTo(ApplicationStatus.SHORTLISTED);
        assertThat(reloaded.getStatusHistory()).hasSize(3);
        assertThat(reloaded.getStatusHistory().get(2).getFromStatus())
                .isEqualTo(ApplicationStatus.UNDER_REVIEW);
    }

    @Test
    @DisplayName("the database rejects a second application from the same candidate to the same job")
    void duplicateApplicationIsRejectedByTheDatabase() {
        CandidateProfile candidate = newCandidate();
        Job job = newPublishedJob();

        Application first = new Application(job, candidate, null);
        first.recordInitialStatus(candidate.getUser());
        em.persist(first);
        em.flush();

        Application duplicate = new Application(job, candidate, null);
        duplicate.recordInitialStatus(candidate.getUser());

        assertThatThrownBy(() -> {
            em.persist(duplicate);
            em.flush();
        }).as("uq_applications_job_candidate must reject the duplicate even when the "
                        + "service-level check has been bypassed")
          .isInstanceOf(PersistenceException.class);
    }
}
