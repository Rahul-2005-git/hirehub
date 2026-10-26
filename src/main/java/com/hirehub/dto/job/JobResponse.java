package com.hirehub.dto.job;

import com.hirehub.dto.company.CompanyResponse;
import com.hirehub.entity.enums.EmploymentType;
import com.hirehub.entity.enums.ExperienceLevel;
import com.hirehub.entity.enums.JobStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record JobResponse(
        Long id,
        String title,
        String description,
        String location,
        EmploymentType employmentType,
        ExperienceLevel experienceLevel,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String currency,
        Integer openings,
        JobStatus status,
        LocalDate applicationDeadline,
        Instant publishedAt,
        Instant closedAt,
        CompanyResponse company,
        List<JobSkillResponse> requiredSkills,
        Instant createdAt,
        Instant updatedAt
) {
}
