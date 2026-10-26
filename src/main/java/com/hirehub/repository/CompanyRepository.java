package com.hirehub.repository;

import com.hirehub.entity.Company;
import com.hirehub.entity.enums.CompanyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    /**
     * Backs the admin moderation queue ({@code GET /api/admin/companies?status=PENDING}).
     * Served by {@code idx_companies_status_created_at} — Spring Data derives
     * {@code ORDER BY created_at} from the {@code Pageable}'s sort, and the equality
     * predicate on {@code status} matches the index's leading column.
     */
    Page<Company> findByStatus(CompanyStatus status, Pageable pageable);
}
