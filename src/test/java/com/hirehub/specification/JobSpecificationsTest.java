package com.hirehub.specification;

import com.hirehub.entity.Company;
import com.hirehub.entity.Job;
import com.hirehub.entity.JobSkill;
import com.hirehub.entity.RecruiterProfile;
import com.hirehub.entity.Skill;
import com.hirehub.entity.enums.EmploymentType;
import com.hirehub.entity.enums.ExperienceLevel;
import com.hirehub.repository.JobRepository;
import com.hirehub.support.AbstractIntegrationTest;
import com.hirehub.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs each {@link JobSpecifications} predicate against real PostgreSQL rather than an
 * in-memory stub — the whole point of {@link JobSpecifications#hasKeyword} is that it
 * generates a specific SQL expression ({@code ILIKE}, matching {@code idx_jobs_title_trgm})
 * rather than an equivalent-looking one that the trigram index can't actually serve; only a
 * real database can tell the two apart; H2 or a hand-rolled in-memory Specification stub
 * would happily accept either.
 */
@Transactional
class JobSpecificationsTest extends AbstractIntegrationTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private TestDataFactory data;

    private Job job(Company company, RecruiterProfile recruiter, String title, String location,
                     EmploymentType type, ExperienceLevel level, String minSalary, String maxSalary) {
        Job job = new Job(company, recruiter, title, "Description for " + title, location, type, level);
        job.setSalaryMin(minSalary == null ? null : new BigDecimal(minSalary));
        job.setSalaryMax(maxSalary == null ? null : new BigDecimal(maxSalary));
        job.publish();
        jobRepository.save(job);
        return job;
    }

    @Test
    @DisplayName("hasKeyword matches case-insensitively and on a partial word")
    void hasKeywordMatchesCaseInsensitivelyAndPartially() {
        Company company = data.persistCompany();
        RecruiterProfile recruiter = data.persistRecruiter(company);
        String marker = UUID.randomUUID().toString().substring(0, 8);
        Job matching = job(company, recruiter, "Senior " + marker + " Engineer", "Pune",
                EmploymentType.FULL_TIME, ExperienceLevel.SENIOR_LEVEL, null, null);
        Job nonMatching = job(company, recruiter, "Marketing Manager", "Pune",
                EmploymentType.FULL_TIME, ExperienceLevel.SENIOR_LEVEL, null, null);

        var results = jobRepository.findAll(JobSpecifications.hasKeyword(marker.toUpperCase()), PageRequest.of(0, 10));

        assertThat(results.getContent()).extracting(Job::getId).contains(matching.getId());
        assertThat(results.getContent()).extracting(Job::getId).doesNotContain(nonMatching.getId());
    }

    @Test
    @DisplayName("hasKeyword with a blank value is a no-op that matches everything")
    void hasKeywordBlankIsNoOp() {
        Company company = data.persistCompany();
        RecruiterProfile recruiter = data.persistRecruiter(company);
        Job anyJob = job(company, recruiter, "Anything " + UUID.randomUUID(), "Pune",
                EmploymentType.FULL_TIME, ExperienceLevel.ENTRY_LEVEL, null, null);

        var results = jobRepository.findAll(JobSpecifications.hasKeyword(""), PageRequest.of(0, 200));

        assertThat(results.getContent()).extracting(Job::getId).contains(anyJob.getId());
    }

    @Test
    @DisplayName("hasLocation matches regardless of case")
    void hasLocationIsCaseInsensitive() {
        Company company = data.persistCompany();
        RecruiterProfile recruiter = data.persistRecruiter(company);
        Job inPune = job(company, recruiter, "Location Test " + UUID.randomUUID(), "Pune",
                EmploymentType.FULL_TIME, ExperienceLevel.ENTRY_LEVEL, null, null);

        var results = jobRepository.findAll(JobSpecifications.hasLocation("PUNE"), PageRequest.of(0, 200));

        assertThat(results.getContent()).extracting(Job::getId).contains(inPune.getId());
    }

    @Test
    @DisplayName("hasMinSalary matches on salaryMax, excluding jobs with no salary range")
    void hasMinSalaryMatchesOnSalaryMax() {
        Company company = data.persistCompany();
        RecruiterProfile recruiter = data.persistRecruiter(company);
        String marker = UUID.randomUUID().toString();
        Job wellPaying = job(company, recruiter, "Well Paying " + marker, "Pune",
                EmploymentType.FULL_TIME, ExperienceLevel.SENIOR_LEVEL, "1500000", "2500000");
        Job lowPaying = job(company, recruiter, "Low Paying " + marker, "Pune",
                EmploymentType.FULL_TIME, ExperienceLevel.ENTRY_LEVEL, "300000", "500000");
        Job noSalary = job(company, recruiter, "No Salary Listed " + marker, "Pune",
                EmploymentType.FULL_TIME, ExperienceLevel.ENTRY_LEVEL, null, null);

        var results = jobRepository.findAll(
                JobSpecifications.hasMinSalary(new BigDecimal("2000000")), PageRequest.of(0, 200));

        assertThat(results.getContent()).extracting(Job::getId).contains(wellPaying.getId());
        assertThat(results.getContent()).extracting(Job::getId)
                .doesNotContain(lowPaying.getId(), noSalary.getId());
    }

    @Test
    @DisplayName("requiresSkill joins through job_skills and matches regardless of case")
    void requiresSkillJoinsCorrectly() {
        Company company = data.persistCompany();
        RecruiterProfile recruiter = data.persistRecruiter(company);
        Skill skill = data.persistSkill("distinctive-search-skill");
        // persistSkill appends a random suffix, so re-read the actual stored name for the search.
        String storedName = skill.getName();

        Job withSkill = job(company, recruiter, "Has The Skill", "Pune",
                EmploymentType.FULL_TIME, ExperienceLevel.MID_LEVEL, null, null);
        withSkill.addSkill(new JobSkill(skill, true));
        Job withoutSkill = job(company, recruiter, "Missing The Skill", "Pune",
                EmploymentType.FULL_TIME, ExperienceLevel.MID_LEVEL, null, null);

        var results = jobRepository.findAll(
                JobSpecifications.requiresSkill(storedName.toUpperCase()), PageRequest.of(0, 200));

        assertThat(results.getContent()).extracting(Job::getId).contains(withSkill.getId());
        assertThat(results.getContent()).extracting(Job::getId).doesNotContain(withoutSkill.getId());
    }

    @Test
    @DisplayName("combined filters compose with AND, not OR")
    void combinedFiltersComposeWithAnd() {
        Company company = data.persistCompany();
        RecruiterProfile recruiter = data.persistRecruiter(company);
        String marker = UUID.randomUUID().toString().substring(0, 8);
        Job matchesBoth = job(company, recruiter, marker + " Engineer", "Pune",
                EmploymentType.FULL_TIME, ExperienceLevel.SENIOR_LEVEL, null, null);
        Job matchesOnlyKeyword = job(company, recruiter, marker + " Engineer", "Mumbai",
                EmploymentType.FULL_TIME, ExperienceLevel.SENIOR_LEVEL, null, null);

        Specification<Job> combined = Specification
                .where(JobSpecifications.hasKeyword(marker))
                .and(JobSpecifications.hasLocation("Pune"));

        var results = jobRepository.findAll(combined, PageRequest.of(0, 200));

        assertThat(results.getContent()).extracting(Job::getId).contains(matchesBoth.getId());
        assertThat(results.getContent()).extracting(Job::getId).doesNotContain(matchesOnlyKeyword.getId());
    }
}
