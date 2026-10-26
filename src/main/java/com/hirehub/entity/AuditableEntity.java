package com.hirehub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Adds {@code created_at} / {@code updated_at} to entities whose tables carry both columns:
 * users, companies, recruiter_profiles, candidate_profiles and jobs.
 *
 * <p>Timestamps are maintained by Spring Data JPA auditing rather than by database triggers.
 * WHY: the in-memory entity and the row agree immediately, so a service can return the new
 * {@code updatedAt} without a re-read, and the behaviour is visible in the Java code instead
 * of hidden in DDL. The columns still have {@code DEFAULT now()} so that manual SQL inserts
 * are not left with nulls.</p>
 *
 * <p>{@code Instant} is used throughout: an absolute point in time, no local-timezone
 * ambiguity. It maps to the {@code TIMESTAMPTZ} columns created in V1.</p>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity extends BaseEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
