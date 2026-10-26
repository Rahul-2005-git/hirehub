package com.hirehub.repository;

import com.hirehub.entity.RecruiterProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecruiterProfileRepository extends JpaRepository<RecruiterProfile, Long> {

    Optional<RecruiterProfile> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    /** Admin/company view: every recruiter working for a given company. */
    Page<RecruiterProfile> findByCompanyId(Long companyId, Pageable pageable);
}
