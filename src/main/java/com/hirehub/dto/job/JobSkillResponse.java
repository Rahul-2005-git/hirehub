package com.hirehub.dto.job;

import com.hirehub.dto.common.SkillResponse;

public record JobSkillResponse(
        SkillResponse skill,
        boolean mandatory
) {
}
