package com.hirehub.mapper;

import com.hirehub.dto.job.JobResponse;
import com.hirehub.dto.job.JobSkillResponse;
import com.hirehub.entity.Job;
import com.hirehub.entity.JobSkill;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class JobMapper {

    private final CompanyMapper companyMapper;
    private final SkillMapper skillMapper;

    public JobMapper(CompanyMapper companyMapper, SkillMapper skillMapper) {
        this.companyMapper = companyMapper;
        this.skillMapper = skillMapper;
    }

    public JobResponse toResponse(Job job) {
        return new JobResponse(
                job.getId(),
                job.getTitle(),
                job.getDescription(),
                job.getLocation(),
                job.getEmploymentType(),
                job.getExperienceLevel(),
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getCurrency(),
                job.getOpenings(),
                job.getStatus(),
                job.getApplicationDeadline(),
                job.getPublishedAt(),
                job.getClosedAt(),
                companyMapper.toResponse(job.getCompany()),
                toSkillResponses(job),
                job.getCreatedAt(),
                job.getUpdatedAt()
        );
    }

    /**
     * Sorted by skill name for the same reason as the candidate-skills equivalent:
     * {@code Job.requiredSkills} is a plain {@code LinkedHashSet} tracking insertion order,
     * not display order.
     */
    private List<JobSkillResponse> toSkillResponses(Job job) {
        return job.getRequiredSkills().stream()
                .sorted(Comparator.comparing(js -> js.getSkill().getName()))
                .map(this::toSkillResponse)
                .toList();
    }

    private JobSkillResponse toSkillResponse(JobSkill jobSkill) {
        return new JobSkillResponse(skillMapper.toResponse(jobSkill.getSkill()), jobSkill.isMandatory());
    }
}
