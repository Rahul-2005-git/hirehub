package com.hirehub.repository;

import com.hirehub.entity.Company;
import com.hirehub.entity.Job;
import com.hirehub.entity.RecruiterProfile;
import com.hirehub.entity.Skill;
import com.hirehub.entity.enums.JobStatus;
import com.hirehub.support.AbstractIntegrationTest;
import com.hirehub.support.TestDataFactory;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class JobRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private TestDataFactory data;

    @Test
    @DisplayName("findWithDetailsById fetches company and required skills without a lazy-load exception")
    void findWithDetailsByIdFetchesTheGraph() {
        Company company = data.persistCompany();
        RecruiterProfile recruiter = data.persistRecruiter(company);
        Job job = data.persistPublishedJob(company, recruiter, "Backend Engineer", "Pune");

        Skill skill = data.persistSkill("kubernetes");
        var jobSkill = new com.hirehub.entity.JobSkill(skill, true);
        job.addSkill(jobSkill);

        Job found = jobRepository.findWithDetailsById(job.getId()).orElseThrow();

        // Detached-safe checks: Hibernate.isInitialized would throw LazyInitializationException
        // instead of returning false if the session were already closed, so a passing
        // assertion here proves the @EntityGraph actually fetched eagerly.
        assertThat(Hibernate.isInitialized(found.getCompany())).isTrue();
        assertThat(found.getCompany().getName()).isEqualTo(company.getName());
        assertThat(Hibernate.isInitialized(found.getRequiredSkills())).isTrue();
        assertThat(found.getRequiredSkills()).hasSize(1);
    }

    @Test
    @DisplayName("findByCreatedById scopes results to the recruiter who actually created them")
    void findByCreatedByIdScopesToTheCreator() {
        Company companyA = data.persistCompany();
        Company companyB = data.persistCompany();
        RecruiterProfile recruiterA = data.persistRecruiter(companyA);
        RecruiterProfile recruiterB = data.persistRecruiter(companyB);

        Job jobA = data.persistPublishedJob(companyA, recruiterA, "Job A", "Pune");
        Job jobB = data.persistPublishedJob(companyB, recruiterB, "Job B", "Mumbai");

        var byRecruiter = jobRepository.findByCreatedById(recruiterB.getId(), PageRequest.of(0, 10));
        assertThat(byRecruiter.getContent()).extracting(Job::getId)
                .containsExactly(jobB.getId());
    }

    @Test
    @DisplayName("JpaSpecificationExecutor is wired: an ad-hoc status Specification filters correctly")
    void specificationExecutorIsWired() {
        Company company = data.persistCompany();
        RecruiterProfile recruiter = data.persistRecruiter(company);
        Job published = data.persistPublishedJob(company, recruiter, "Published Role", "Pune");

        Specification<Job> onlyPublished =
                (root, query, cb) -> cb.equal(root.get("status"), JobStatus.PUBLISHED);

        var results = jobRepository.findAll(onlyPublished, PageRequest.of(0, 10));

        assertThat(results.getContent()).extracting(Job::getId).contains(published.getId());
        assertThat(results.getContent()).allMatch(j -> j.getStatus() == JobStatus.PUBLISHED);
    }

    @Test
    @DisplayName("findWithDetailsById returns empty for a non-existent id rather than throwing")
    void findWithDetailsByIdIsEmptyForMissingJob() {
        Optional<Job> result = jobRepository.findWithDetailsById(-1L);
        assertThat(result).isEmpty();
    }
}
