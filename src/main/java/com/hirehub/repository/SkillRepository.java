package com.hirehub.repository;

import com.hirehub.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, Long> {

    /**
     * Names are normalised to lower case before every write (see {@code Skill.normalize}),
     * so a case-sensitive lookup is correct and can use the plain {@code uq_skills_name}
     * index rather than a functional one.
     */
    Optional<Skill> findByName(String normalizedName);

    /** Type-ahead search for the "add a skill" UI. */
    @Query("select s from Skill s where s.name like concat(:prefix, '%') order by s.name")
    List<Skill> findByNameStartingWith(@Param("prefix") String normalizedPrefix);

    List<Skill> findByIdIn(List<Long> ids);
}
