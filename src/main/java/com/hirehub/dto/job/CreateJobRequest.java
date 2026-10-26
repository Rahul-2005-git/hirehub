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
 * A new job always starts {@code DRAFT} (the entity's default) — there is deliberately no
 * {@code status} field here, the same reasoning as {@code CreateCompanyRequest} having none:
 * a status transition is a distinct, guarded action ({@code PATCH .../publish}), never
 * something set directly by the create request.
 *
 * <p>{@code isSalaryRangeValid()} is a Bean Validation cross-field check expressed as a
 * plain {@code @AssertTrue} method rather than a custom class-level annotation — records
 * support instance methods beyond their canonical accessors, and Hibernate Validator picks
 * up any {@code isXxx()}/{@code getXxx()} method annotated this way the same as it would on
 * a regular class. A custom annotation would be more ceremony for a rule used in exactly one
 * place.</p>
 */
public record CreateJobRequest(

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
