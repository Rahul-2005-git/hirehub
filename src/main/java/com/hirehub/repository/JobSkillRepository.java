package com.hirehub.repository;

import com.hirehub.entity.JobSkill;
import com.hirehub.entity.JobSkillId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Most reads go through {@code Job.getRequiredSkills()}. This repository backs the reverse
 * lookup "jobs requiring skill Y", used as one of the job-search filters in Phase 9.
 */
public interface JobSkillRepository extends JpaRepository<JobSkill, JobSkillId> {

    Page<JobSkill> findBySkillId(Long skillId, Pageable pageable);
}
