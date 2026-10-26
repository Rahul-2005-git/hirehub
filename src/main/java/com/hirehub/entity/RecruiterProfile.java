package com.hirehub.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Recruiter-specific data, one row per recruiter {@link User}.
 *
 * <p>The one-to-one is owned here and enforced by {@code uq_recruiter_profiles_user}: the
 * FK column plus a UNIQUE constraint is what distinguishes one-to-one from one-to-many.
 * The association is unidirectional (profile → user) so that loading a user never drags a
 * profile along.</p>
 */
@Entity
@Table(name = "recruiter_profiles")
public class RecruiterProfile extends AuditableEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * Nullable on purpose: a recruiter registers first and creates or joins a company
     * afterwards. Services must therefore treat "recruiter without a company" as a valid
     * state and refuse job creation until it is set.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "designation", length = 100)
    private String designation;

    protected RecruiterProfile() {
        // required by JPA
    }

    public RecruiterProfile(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public boolean hasCompany() {
        return company != null;
    }

    @Override
    public String toString() {
        return "RecruiterProfile{id=" + getId() + ", designation='" + designation + "'}";
    }
}
