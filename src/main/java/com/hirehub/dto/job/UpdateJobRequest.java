package com.hirehub.dto.job;

import com.hirehub.entity.enums.EmploymentType;
import com.hirehub.entity.enums.ExperienceLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Kept as a separate type from {@link CreateJobRequest} despite the identical shape today,
 * consistent with {@code UpdateCompanyRequest} vs {@code CreateCompanyRequest} elsewhere in
 * the app: the two operations have different rules about *when* they're allowed (a job can
 * only be updated while {@code DRAFT} or {@code PUBLISHED} — see
 * {@code JobService.updateJob}) even though the request body looks the same today, and a
 * shared type would make that coincidence load-bearing the moment one of them needs a field
 * the other doesn't.
 *
 * <p>Replaces the full skill list rather than adding/removing individual entries (unlike
 * candidate skills, which have dedicated add/remove endpoints): a job posting's skill list
 * is edited as a whole far more often than incrementally, so "send the list you want" is the
 * simpler contract here.</p>
 */
public record UpdateJobRequest(

        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,

        @NotBlank(message = "Description is required")
        @Size(max = 20000, message = "Description must be at most 20000 characters")
        String description,

        @NotBlank(message = "Location is required")
        @Size(max = 120, message = "Location must be at most 120 characters")
        String location,

        @NotNull(message = "Employment type is required")
        EmploymentType employmentType,

        @NotNull(message = "Experience level is required")
        ExperienceLevel experienceLevel,

        @DecimalMin(value = "0.0", message = "Minimum salary cannot be negative")
        BigDecimal salaryMin,

        @DecimalMin(value = "0.0", message = "Maximum salary cannot be negative")
        BigDecimal salaryMax,

        @Pattern(regexp = "^$|^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code, e.g. INR")
        String currency,

        @Min(value = 1, message = "Openings must be at least 1")
        Integer openings,

        @FutureOrPresent(message = "Application deadline cannot be in the past")
        LocalDate applicationDeadline,

        @Valid
        List<JobSkillRequest> skills
) {
    @AssertTrue(message = "Maximum salary must not be less than minimum salary")
    public boolean isSalaryRangeValid() {
        return salaryMin == null || salaryMax == null || salaryMax.compareTo(salaryMin) >= 0;
    }
}
