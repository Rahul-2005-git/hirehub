package com.hirehub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Locale;

/**
 * A shared skill in the vocabulary, referenced by both candidates and jobs.
 *
 * <p>Names are normalised to lower case in the constructor so that "Java", "java" and
 * "JAVA" resolve to the same row. The database backs this up with
 * {@code ck_skills_name_lowercase}, so a stray insert from anywhere else is rejected rather
 * than quietly creating a duplicate vocabulary entry.</p>
 *
 * <p>Extends {@link BaseEntity} rather than {@link AuditableEntity}: the table has only
 * {@code created_at}, because a skill name is never edited — a wrong one is deleted.</p>
 */
@Entity
@Table(name = "skills")
@EntityListeners(AuditingEntityListener.class)
public class Skill extends BaseEntity {

    @Column(name = "name", nullable = false, unique = true, length = 80)
    private String name;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Skill() {
        // required by JPA
    }

    public Skill(String name) {
        this.name = normalize(name);
    }

    /** Single place that defines what "the same skill" means. */
    public static String normalize(String rawName) {
        return rawName == null ? null : rawName.trim().toLowerCase(Locale.ROOT);
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "Skill{id=" + getId() + ", name='" + name + "'}";
    }
}
