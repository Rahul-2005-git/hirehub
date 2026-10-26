package com.hirehub.entity;

import com.hirehub.entity.enums.ApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * One immutable entry in an application's audit trail.
 *
 * <p>Every field is written once, at construction: there are no setters, which is the
 * clearest possible statement that entries are never edited. The package-private
 * constructor means only {@link Application} can create one, so history cannot be forged
 * from elsewhere in the codebase.</p>
 *
 * <p>{@code changedByUserId} is nullable and its FK is {@code ON DELETE SET NULL}: deleting
 * a recruiter's account must not erase the decisions they recorded. The trail outlives the
 * actor.</p>
 */
@Entity
@Table(name = "application_status_history")
@EntityListeners(AuditingEntityListener.class)
public class ApplicationStatusHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private Application application;

    /** Null for the first entry, where there is no previous status. */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20, updatable = false)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20, updatable = false)
    private ApplicationStatus toStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id", updatable = false)
    private User changedBy;

    @Column(name = "note", columnDefinition = "text", updatable = false)
    private String note;

    @CreatedDate
    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    protected ApplicationStatusHistory() {
        // required by JPA
    }

    ApplicationStatusHistory(Application application, ApplicationStatus fromStatus,
                             ApplicationStatus toStatus, User changedBy, String note) {
        this.application = application;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.note = note;
    }

    public Application getApplication() {
        return application;
    }

    public ApplicationStatus getFromStatus() {
        return fromStatus;
    }

    public ApplicationStatus getToStatus() {
        return toStatus;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public String getNote() {
        return note;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    @Override
    public String toString() {
        return "ApplicationStatusHistory{" + fromStatus + " -> " + toStatus + " at " + changedAt + "}";
    }
}
