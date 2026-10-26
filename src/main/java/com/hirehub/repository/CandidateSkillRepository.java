package com.hirehub.repository;

import com.hirehub.entity.CandidateSkill;
import com.hirehub.entity.CandidateSkillId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Most reads go through {@code CandidateProfile.getSkills()} — this repository exists for
 * the reverse lookup the aggregate cannot answer efficiently: "which candidates know skill
 * Y", used by the recruiter's candidate search (Phase 7).
 */
public interface CandidateSkillRepository extends JpaRepository<CandidateSkill, CandidateSkillId> {

    Page<CandidateSkill> findBySkillId(Long skillId, Pageable pageable);
}
