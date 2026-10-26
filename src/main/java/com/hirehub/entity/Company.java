package com.hirehub.entity;

import com.hirehub.entity.enums.CompanyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * An employer. Created by a recruiter, then approved or rejected by an admin before its
 * jobs may be published.
 *
 * <p>The {@code jobs} and {@code recruiters} collections are not mapped. WHY: a company can
 * accumulate thousands of jobs, and an entity-level collection invites accidental full
 * loads and makes paging impossible. Those reads go through {@code JobRepository} with a
 * {@code Pageable} instead. Mapping a collection just because the relationship exists is
 * one of the most common JPA performance mistakes.</p>
 */
@Entity
@Table(name = "companies")
public class Company extends AuditableEntity {

    @Column(name = "name", nullable = false, unique = true, length = 150)
    private String name;

    @Column(name = "website", length = 255)
    private String website;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "company_size", length = 30)
    private String companySize;

    @Column(name = "logo_url", length = 512)
    private String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CompanyStatus status = CompanyStatus.PENDING;

    protected Company() {
        // required by JPA
    }

    public Company(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getCompanySize() {
        return companySize;
    }

    public void setCompanySize(String companySize) {
        this.companySize = companySize;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public CompanyStatus getStatus() {
        return status;
    }

    public void setStatus(CompanyStatus status) {
        this.status = status;
    }

    public boolean isApproved() {
        return status == CompanyStatus.APPROVED;
    }

    @Override
    public String toString() {
        return "Company{id=" + getId() + ", name='" + name + "', status=" + status + "}";
    }
}
