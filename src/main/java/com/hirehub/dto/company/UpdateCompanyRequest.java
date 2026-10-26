package com.hirehub.dto.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Same optional-but-validated shape as {@code UpdateCandidateProfileRequest}: a full
 * replace of the editable fields, but nothing here is required at the type level beyond
 * what genuinely cannot be blank ({@code name} — a company without one isn't a company).
 * No {@code status} field, for the same reason {@link CreateCompanyRequest} has none: only
 * an admin action changes it.
 */
public record UpdateCompanyRequest(

        @NotBlank(message = "Company name is required")
        @Size(max = 150, message = "Company name must be at most 150 characters")
        String name,

        @Pattern(regexp = "^$|^https?://.+", message = "Website must be a valid http(s) URL")
        @Size(max = 255)
        String website,

        @Size(max = 4000, message = "Description must be at most 4000 characters")
        String description,

        @Size(max = 100)
        String industry,

        @Size(max = 30)
        String companySize
) {
}
