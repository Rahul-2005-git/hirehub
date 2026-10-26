package com.hirehub.dto.candidate;

import com.hirehub.entity.enums.ProficiencyLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param skillName a free-text name, not a skill id. WHY: the skill vocabulary is shared
 *                   and open-ended (see migration V2's starter list) — requiring the client
 *                   to already know a skill's id would mean a separate "look up the id
 *                   first" round trip for every skill not already memorized. The service
 *                   normalizes and finds-or-creates the matching {@code Skill} row, the same
 *                   way {@code Skill}'s own constructor normalizes on the write side.
 */
public record AddCandidateSkillRequest(

        @NotBlank(message = "Skill name is required")
        @Size(max = 80, message = "Skill name must be at most 80 characters")
        String skillName,

        @NotNull(message = "Proficiency is required")
        ProficiencyLevel proficiency,

        @Min(value = 0, message = "Years of experience cannot be negative")
        @Max(value = 60, message = "Years of experience must be realistic")
        Integer yearsOfExperience
) {
}
