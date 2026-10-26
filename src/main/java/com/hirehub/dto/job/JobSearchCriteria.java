package com.hirehub.dto.job;

import com.hirehub.entity.enums.EmploymentType;
import com.hirehub.entity.enums.ExperienceLevel;

import java.math.BigDecimal;

/**
 * Every field is optional — this binds {@code GET /api/jobs}'s query parameters
 * (e.g. {@code ?keyword=java&location=Pune&minSalary=800000}), not a JSON body, via
 * {@code @ModelAttribute} in {@code JobController}. No Bean Validation constraints: an
 * absent or blank filter simply means "don't filter on this", which
 * {@code JobSpecifications} already treats as a no-op predicate — there is nothing here
 * that would be invalid to omit.
 */
public record JobSearchCriteria(
        String keyword,
        String location,
        EmploymentType employmentType,
        ExperienceLevel experienceLevel,
        BigDecimal minSalary,
        BigDecimal maxSalary,
        String skill
) {
}
