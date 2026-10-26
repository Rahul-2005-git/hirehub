package com.hirehub.mapper;

import com.hirehub.dto.candidate.CandidateProfileResponse;
import com.hirehub.dto.candidate.CandidateSkillResponse;
import com.hirehub.entity.CandidateProfile;
import com.hirehub.entity.CandidateSkill;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class CandidateProfileMapper {

    private final SkillMapper skillMapper;

    public CandidateProfileMapper(SkillMapper skillMapper) {
        this.skillMapper = skillMapper;
    }

    public CandidateProfileResponse toResponse(CandidateProfile profile) {
        return new CandidateProfileResponse(
                profile.getId(),
                profile.getUser().getFullName(),
                profile.getUser().getEmail(),
                profile.getHeadline(),
                profile.getSummary(),
                profile.getExperienceYears(),
                profile.getCurrentLocation(),
                profile.getExpectedSalary(),
                profile.getResumeUrl(),
                profile.getResumeFileName(),
                profile.getLinkedinUrl(),
                profile.getGithubUrl(),
                toSkillResponses(profile)
        );
    }

    /**
     * Sorted by skill name rather than left in whatever order the {@code Set} iterates:
     * {@code CandidateProfile.skills} is a plain {@code LinkedHashSet}, so its order tracks
     * insertion, not anything meaningful to a client — an alphabetical list is what a
     * profile page actually wants to render.
     */
    private List<CandidateSkillResponse> toSkillResponses(CandidateProfile profile) {
        return profile.getSkills().stream()
                .sorted(Comparator.comparing(cs -> cs.getSkill().getName()))
                .map(this::toSkillResponse)
                .toList();
    }

    public CandidateSkillResponse toSkillResponse(CandidateSkill candidateSkill) {
        return new CandidateSkillResponse(
                skillMapper.toResponse(candidateSkill.getSkill()),
                candidateSkill.getProficiency(),
                candidateSkill.getYearsOfExperience()
        );
    }
}
