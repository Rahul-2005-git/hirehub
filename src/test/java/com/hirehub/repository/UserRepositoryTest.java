package com.hirehub.repository;

import com.hirehub.entity.User;
import com.hirehub.entity.enums.CompanyStatus;
import com.hirehub.entity.enums.RoleName;
import com.hirehub.entity.enums.UserStatus;
import com.hirehub.support.AbstractIntegrationTest;
import com.hirehub.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each test method runs in its own transaction, rolled back afterwards, so tests can run
 * in any order against the container shared by the whole suite without needing to clean up
 * after themselves.
 */
@Transactional
class UserRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private TestDataFactory data;

    @Test
    @DisplayName("email lookup is case-insensitive, matching how login normalises input")
    void findByEmailIgnoreCase() {
        User user = data.persistUser(RoleName.CANDIDATE);

        assertThat(userRepository.findByEmailIgnoreCase(user.getEmail().toUpperCase())).isPresent();
        assertThat(userRepository.existsByEmailIgnoreCase(user.getEmail())).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCase("nobody@hirehub.test")).isFalse();
    }

    @Test
    @DisplayName("search filters by role and status independently, and combined")
    void searchFiltersByRoleAndStatus() {
        User candidate = data.persistUser(RoleName.CANDIDATE);
        User recruiter = data.persistUser(RoleName.RECRUITER);
        recruiter.setStatus(UserStatus.DISABLED);

        var byRoleOnly = userRepository.search(RoleName.CANDIDATE, null, PageRequest.of(0, 50));
        assertThat(byRoleOnly.getContent()).extracting(User::getId).contains(candidate.getId());
        assertThat(byRoleOnly.getContent()).extracting(User::getId).doesNotContain(recruiter.getId());

        var byStatusOnly = userRepository.search(null, UserStatus.DISABLED, PageRequest.of(0, 50));
        assertThat(byStatusOnly.getContent()).extracting(User::getId).contains(recruiter.getId());

        var byBoth = userRepository.search(RoleName.RECRUITER, UserStatus.DISABLED, PageRequest.of(0, 50));
        assertThat(byBoth.getContent()).extracting(User::getId).containsExactly(recruiter.getId());
    }

    @Test
    @DisplayName("company name lookup is case-insensitive and the moderation queue filters by status")
    void companyLookupAndModerationQueue() {
        var company = data.persistCompany();

        assertThat(companyRepository.findByNameIgnoreCase(company.getName().toUpperCase())).isPresent();
        assertThat(companyRepository.existsByNameIgnoreCase(company.getName())).isTrue();

        var pending = companyRepository.findByStatus(CompanyStatus.PENDING, PageRequest.of(0, 50));
        assertThat(pending.getContent()).extracting(c -> c.getId()).contains(company.getId());

        var approved = companyRepository.findByStatus(CompanyStatus.APPROVED, PageRequest.of(0, 50));
        assertThat(approved.getContent()).extracting(c -> c.getId()).doesNotContain(company.getId());
    }
}
