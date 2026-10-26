package com.hirehub.support;

import com.hirehub.entity.CandidateProfile;
import com.hirehub.entity.Company;
import com.hirehub.entity.Job;
import com.hirehub.entity.RecruiterProfile;
import com.hirehub.entity.Role;
import com.hirehub.entity.Skill;
import com.hirehub.entity.User;
import com.hirehub.entity.enums.EmploymentType;
import com.hirehub.entity.enums.ExperienceLevel;
import com.hirehub.entity.enums.RoleName;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Builds and persists minimal-but-valid entity graphs for tests.
 *
 * <p>Centralising this stops every repository test from re-deriving "what does a legal
 * {@code Job} require" and keeps that knowledge in one place as the entities evolve.
 * Emails, company names and skill names are randomised per call so tests can run in any
 * order against the shared container without colliding on a unique constraint.</p>
 */
@Component
public class TestDataFactory {

    private final EntityManager em;

    public TestDataFactory(EntityManager em) {
        this.em = em;
    }

    public Role role(RoleName name) {
        return em.createQuery("select r from Role r where r.name = :n", Role.class)
                .setParameter("n", name)
                .getSingleResult();
    }

    public User persistUser(RoleName roleName) {
        User user = new User(uniqueEmail(), "$2a$10$notarealhashnotarealhashnotarealhash",
                "Test User " + roleName, role(roleName));
        em.persist(user);
        return user;
    }

    public CandidateProfile persistCandidate() {
        CandidateProfile profile = new CandidateProfile(persistUser(RoleName.CANDIDATE));
        profile.setHeadline("Backend Engineer");
        profile.setResumeUrl("https://files.example.test/resume.pdf");
        profile.setExperienceYears(3);
        profile.setExpectedSalary(new BigDecimal("1200000.00"));
        em.persist(profile);
        return profile;
    }

    public Company persistCompany() {
        Company company = new Company("Test Co " + UUID.randomUUID());
        em.persist(company);
        return company;
    }

    public RecruiterProfile persistRecruiter(Company company) {
        RecruiterProfile recruiter = new RecruiterProfile(persistUser(RoleName.RECRUITER));
        recruiter.setCompany(company);
        em.persist(recruiter);
        return recruiter;
    }

    /** A published, open job — the shape most search/pipeline tests need. */
    public Job persistPublishedJob(Company company, RecruiterProfile recruiter, String title, String location) {
        Job job = new Job(company, recruiter, title, "Description for " + title, location,
                EmploymentType.FULL_TIME, ExperienceLevel.SENIOR_LEVEL);
        job.setSalaryMin(new BigDecimal("1500000.00"));
        job.setSalaryMax(new BigDecimal("2500000.00"));
        job.setApplicationDeadline(LocalDate.now().plusDays(30));
        job.publish();
        em.persist(job);
        return job;
    }

    public Skill persistSkill(String name) {
        Skill skill = new Skill(name + "-" + UUID.randomUUID());
        em.persist(skill);
        return skill;
    }

    public Skill findSeededSkill(String name) {
        return em.createQuery("select s from Skill s where s.name = :n", Skill.class)
                .setParameter("n", name)
                .getSingleResult();
    }

    public String uniqueEmail() {
        return UUID.randomUUID() + "@hirehub.test";
    }
}
