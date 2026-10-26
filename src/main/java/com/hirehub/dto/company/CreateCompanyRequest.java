package com.hirehub.dto.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Every new company starts {@code PENDING} (the entity's default) regardless of what the
 * request contains — there is deliberately no {@code status} field here for a client to
 * set. Approval is an admin action ({@code AdminCompanyController}), never something the
 * creator can grant themselves.
 */
public record CreateCompanyRequest(

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
