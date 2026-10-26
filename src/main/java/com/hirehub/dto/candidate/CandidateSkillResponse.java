package com.hirehub.dto.candidate;

import com.hirehub.dto.common.SkillResponse;
import com.hirehub.entity.enums.ProficiencyLevel;

public record CandidateSkillResponse(
        SkillResponse skill,
        ProficiencyLevel proficiency,
        Integer yearsOfExperience
) {
}
