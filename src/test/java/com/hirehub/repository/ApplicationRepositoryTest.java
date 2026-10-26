package com.hirehub.repository;

import com.hirehub.entity.Application;
import com.hirehub.entity.CandidateProfile;
import com.hirehub.entity.Company;
import com.hirehub.entity.Job;
import com.hirehub.entity.RecruiterProfile;
import com.hirehub.entity.enums.ApplicationStatus;
import com.hirehub.support.AbstractIntegrationTest;
import com.hirehub.support.TestDataFactory;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ApplicationRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private TestDataFactory data;

    private Application apply(Job job, CandidateProfile candidate) {
        Application application = new Application(job, candidate, null);
        application.recordInitialStatus(candidate.getUser());
        applicationRepository.save(application);
        return application;
    }

    @Test
    @DisplayName("existsByJobIdAndCandidateId is the service-level half of the duplicate guard")
    void existsByJobAndCandidate() {
        Company company = data.persistCompany();
        RecruiterProfile recruiter = data.persistRecruiter(company);
        Job job = data.persistPublishedJob(company, recruiter, "Engineer", "Pune");
        CandidateProfile candidate = data.persistCandidate();

        assertThat(applicationRepository.existsByJobIdAndCandidateId(job.getId(), candidate.getId())).isFalse();

        apply(job, candidate);

        assertThat(applicationRepository.existsByJobIdAndCandidateId(job.getId(), candidate.getId())).isTrue();
        assertThat(applicationRepository.findByJobIdAndCandidateId(job.getId(), candidate.getId())).isPresent();
    }

    @Test
    @DisplayName("candidate and recruiter dashboards are scoped and status-filterable")
    void dashboardsAreScopedAndFilterable() {
        Company company = data.persistCompany();
        RecruiterProfile recruiter = data.persistRecruiter(company);
        Job jobA = data.persistPublishedJob(company, recruiter, "Role A", "Pune");
        Job jobB = data.persistPublishedJob(company, recruiter, "Role B", "Pune");
        CandidateProfile candidate = data.persistCandidate();

        Application appA = apply(jobA, candidate);
        apply(jobB, candidate);
        appA.changeStatus(ApplicationStatus.UNDER_REVIEW, recruiter.getUser(), null);

        var allForCandidate = applicationRepository.findByCandidateId(candidate.getId(), PageRequest.of(0, 10));
        assertThat(allForCandidate.getTotalElements()).isEqualTo(2);

        var underReviewForCandidate = applicationRepository.findByCandidateIdAndStatus(
                candidate.getId(), ApplicationStatus.UNDER_REVIEW, PageRequest.of(0, 10));
        assertThat(underReviewForCandidate.getContent()).extracting(Application::getId)
                .containsExactly(appA.getId());

        var pipelineForJobA = applicationRepository.findByJobId(jobA.getId(), PageRequest.of(0, 10));
        assertThat(pipelineForJobA.getContent()).extracting(Application::getId)
                .containsExactly(appA.getId());

        assertThat(applicationRepository.countByJobIdAndStatus(jobA.getId(), ApplicationStatus.UNDER_REVIEW))
                .isEqualTo(1);
        assertThat(applicationRepository.countByJobIdAndStatus(jobA.getId(), ApplicationStatus.APPLIED))
                .isZero();
    }

    @Test
    @DisplayName("findByCandidateId fetches job.company and candidate.user eagerly - no N+1 per row")
    void findByCandidateIdAvoidsNPlusOne() {
        Company company = data.persistCompany();
        RecruiterProfile recruiter = data.persistRecruiter(company);
        Job job = data.persistPublishedJob(company, recruiter, "Engineer", "Pune");
        CandidateProfile candidate = data.persistCandidate();
        apply(job, candidate);

        // Checked immediately after the query returns, before any getter is called that
        // could trigger the very lazy-load this is meant to rule out - see the reasoning in
        // findWithHistoryByIdFetchesTheTimeline above for why order matters here.
        Application found = applicationRepository.findByCandidateId(candidate.getId(), PageRequest.of(0, 10))
                .getContent().getFirst();

        assertThat(Hibernate.isInitialized(found.getJob())).isTrue();
        assertThat(Hibernate.isInitialized(found.getJob().getCompany())).isTrue();
        assertThat(Hibernate.isInitialized(found.getCandidate())).isTrue();
        assertThat(Hibernate.isInitialized(found.getCandidate().getUser())).isTrue();
    }

    @Test
    @DisplayName("findWithHistoryById fetches the full timeline without a lazy-load exception")
    void findWithHistoryByIdFetchesTheTimeline() {
        Company company = data.persistCompany();
        RecruiterProfile recruiter = data.persistRecruiter(company);
        Job job = data.persistPublishedJob(company, recruiter, "Engineer", "Pune");
        CandidateProfile candidate = data.persistCandidate();

        Application application = apply(job, candidate);
        application.changeStatus(ApplicationStatus.UNDER_REVIEW, recruiter.getUser(), "Looks promising");

        Application found = applicationRepository.findWithHistoryById(application.getId()).orElseThrow();

        // Application.getStatusHistory() defensively copies the collection, so checking
        // Hibernate.isInitialized on its result would be trivially true either way and
        // would not actually prove the @EntityGraph did anything. job/candidate are plain
        // @ManyToOne fields, so checking them before any getter touches them is meaningful.
        assertThat(Hibernate.isInitialized(found.getJob())).isTrue();
        assertThat(Hibernate.isInitialized(found.getCandidate())).isTrue();
        assertThat(found.getStatusHistory()).hasSize(2);
    }
}
