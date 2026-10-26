package com.hirehub.dto.job;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Same free-text-name-not-id shape as {@code AddCandidateSkillRequest}, and for the same
 * reason: the skill vocabulary is shared and open-ended, so requiring a client to resolve a
 * name to an id first would be an unnecessary round trip. The service finds-or-creates the
 * matching {@code Skill} row exactly the way candidate skills already do.
 */
public record JobSkillRequest(

        @NotBlank(message = "Skill name is required")
        @Size(max = 80, message = "Skill name must be at most 80 characters")
        String skillName,

        @NotNull(message = "Mandatory flag is required")
        Boolean mandatory
) {
}
