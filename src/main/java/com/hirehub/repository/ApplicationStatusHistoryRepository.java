package com.hirehub.repository;

import com.hirehub.entity.ApplicationStatusHistory;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * {@link Application#getStatusHistory()} already returns the full, ordered trail for a
 * loaded application, so most code never needs this repository directly. It exists for the
 * cases that read history without loading the whole application aggregate — the admin
 * activity feed and reporting queries in later phases.
 *
 * <p>No custom delete or update methods are declared here: the table is append-only by
 * design (see the entity's Javadoc). {@code JpaRepository} still inherits a generic
 * {@code delete(...)}, but nothing in the service layer calls it for this type — the
 * convention is enforced by code review, not by the type system.</p>
 */
public interface ApplicationStatusHistoryRepository extends JpaRepository<ApplicationStatusHistory, Long> {

    List<ApplicationStatusHistory> findByApplicationId(Long applicationId, Sort sort);
}
