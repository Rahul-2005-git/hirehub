package com.hirehub.mapper;

import com.hirehub.dto.common.SkillResponse;
import com.hirehub.entity.Skill;
import org.springframework.stereotype.Component;

/**
 * Split out on its own rather than left inside {@code CandidateProfileMapper} (where it
 * started): once {@code JobMapper} needed the identical {@code Skill -> SkillResponse}
 * mapping, leaving it in a candidate-specific class would have meant either duplicating it
 * or having the job feature reach into the candidate feature for something with nothing
 * candidate-specific about it. {@code SkillResponse} already lives in {@code dto.common}
 * for exactly this reuse reason; the mapper now mirrors that placement.
 */
@Component
public class SkillMapper {

    public SkillResponse toResponse(Skill skill) {
        return new SkillResponse(skill.getId(), skill.getName());
    }
}
