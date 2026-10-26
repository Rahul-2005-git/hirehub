package com.hirehub.dto.candidate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Every field is optional at the type level (no {@code @NotBlank}/{@code @NotNull}) even
 * though this backs a {@code PUT}. WHY: {@code PUT /api/candidates/me} replaces the editable
 * profile fields as a whole, but a candidate is not required to have filled in, say, a
 * GitHub URL — the constraint that matters is "if a field is present, is it well-formed",
 * not "is every field present". Requiring every field would force a candidate to re-submit
 * data they have no way to know unless the client always fetches-then-resends the full
 * profile first.
 *
 * <p>The URL fields use a blank-tolerant {@code @Pattern} rather than Hibernate Validator's
 * {@code @URL}: {@code @URL} rejects an empty string outright, which would make "clear this
 * field" impossible to express — the same reasoning behind {@code RegisterRequest.phone}'s
 * pattern.</p>
 *
 * <p>{@code resumeUrl} and {@code resumeFileName} live here rather than behind a separate
 * {@code PUT /api/candidates/me/resume} endpoint (as originally sketched in API.md): there
 * is no file-storage integration in this project's scope (no S3 or equivalent), so "upload
 * a resume" is, in practice, "record a link to one" — exactly what this endpoint already
 * does for every other profile field. A dedicated endpoint for the same one write operation
 * would be a distinction without a difference.</p>
 */
public record UpdateCandidateProfileRequest(

        @Size(max = 150, message = "Headline must be at most 150 characters")
        String headline,

        @Size(max = 4000, message = "Summary must be at most 4000 characters")
        String summary,

        @Min(value = 0, message = "Experience years cannot be negative")
        @Max(value = 60, message = "Experience years must be realistic")
        Integer experienceYears,

        @Size(max = 120, message = "Location must be at most 120 characters")
        String currentLocation,

        @DecimalMin(value = "0.0", message = "Expected salary cannot be negative")
        BigDecimal expectedSalary,

        @Pattern(regexp = "^$|^https?://.+", message = "Resume URL must be a valid http(s) URL")
        @Size(max = 512, message = "Resume URL must be at most 512 characters")
        String resumeUrl,

        @Size(max = 255, message = "Resume file name must be at most 255 characters")
        String resumeFileName,

        @Pattern(regexp = "^$|^https?://.+", message = "LinkedIn URL must be a valid http(s) URL")
        @Size(max = 255)
        String linkedinUrl,

        @Pattern(regexp = "^$|^https?://.+", message = "GitHub URL must be a valid http(s) URL")
        @Size(max = 255)
        String githubUrl
) {
}
