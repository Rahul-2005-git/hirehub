package com.hirehub.service;

import com.hirehub.entity.Skill;
import com.hirehub.repository.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Extracted once {@code JobService} needed the exact same find-or-create logic
 * {@code CandidateProfileService} already had written privately (Phase 5+6) — rather than a
 * second copy of the same three lines, both now depend on this.
 */
@Service
public class SkillService {

    private final SkillRepository skillRepository;

    public SkillService(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    /**
     * Finds the shared {@link Skill} row for a name, creating it if this is the first time
     * anyone has referenced it. Names are normalized the same way {@code Skill}'s own
     * constructor normalizes them, so "Java" and "java" resolve to one row regardless of
     * which candidate or recruiter typed which capitalization first.
     */
    @Transactional
    public Skill findOrCreateSkill(String rawName) {
        String normalized = Skill.normalize(rawName);
        return skillRepository.findByName(normalized)
                .orElseGet(() -> skillRepository.save(new Skill(normalized)));
    }
}
