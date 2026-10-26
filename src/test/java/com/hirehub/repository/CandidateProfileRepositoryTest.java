package com.hirehub.repository;

import com.hirehub.entity.CandidateProfile;
import com.hirehub.entity.CandidateSkill;
import com.hirehub.entity.Skill;
import com.hirehub.entity.enums.ProficiencyLevel;
import com.hirehub.support.AbstractIntegrationTest;
import com.hirehub.support.TestDataFactory;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class CandidateProfileRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private TestDataFactory data;

    @Test
    @DisplayName("findByIdWithSkills fetches the profile's skills without a lazy-load exception")
    void findByIdWithSkillsFetchesTheGraph() {
        CandidateProfile profile = data.persistCandidate();
        Skill skill = data.persistSkill("golang");
        profile.addSkill(new CandidateSkill(skill, ProficiencyLevel.EXPERT, 5));

        CandidateProfile found = candidateProfileRepository.findByIdWithSkills(profile.getId()).orElseThrow();

        assertThat(Hibernate.isInitialized(found.getSkills())).isTrue();
        assertThat(found.getSkills()).hasSize(1);
    }

    @Test
    @DisplayName("findByUserId and existsByUserId back the one-to-one lookup used by /candidates/me")
    void findByUserId() {
        CandidateProfile profile = data.persistCandidate();

        assertThat(candidateProfileRepository.existsByUserId(profile.getUser().getId())).isTrue();
        assertThat(candidateProfileRepository.findByUserId(profile.getUser().getId()))
                .map(CandidateProfile::getId)
                .contains(profile.getId());
    }

    @Test
    @DisplayName("skill name-prefix search supports the add-a-skill type-ahead")
    void skillPrefixSearch() {
        Skill skill = skillRepository.save(new Skill("distinctive-prefix-" + java.util.UUID.randomUUID()));

        var results = skillRepository.findByNameStartingWith("distinctive-prefix");

        assertThat(results).extracting(Skill::getId).contains(skill.getId());
    }

    @Test
    @DisplayName("seeded reference skills are findable by their exact normalized name")
    void findsSeededSkill() {
        assertThat(skillRepository.findByName("java")).isPresent();
        assertThat(skillRepository.findByName("java").orElseThrow().getName()).isEqualTo("java");
    }
}
