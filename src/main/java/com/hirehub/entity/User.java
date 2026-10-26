package com.hirehub.entity;

import com.hirehub.entity.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Authentication identity. Deliberately narrow: profile data lives in
 * {@link CandidateProfile} / {@link RecruiterProfile} so that the login lookup reads one
 * small row.
 *
 * <p>The profile association is intentionally <b>not</b> mapped here. A bidirectional
 * {@code @OneToOne} would either force an eager join on every authentication or produce a
 * surprise extra query; services load the profile through its own repository when they
 * actually need it.</p>
 */
@Entity
@Table(name = "users")
public class User extends AuditableEntity {

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /**
     * BCrypt hash, never a plain password. 100 chars leaves room for the 60-char BCrypt
     * output plus a future algorithm prefix. Excluded from toString and from every DTO.
     */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "phone", length = 20)
    private String phone;

    // LAZY: authorization needs the role name, but many reads do not, and an eager join
    // on a lookup table would be paid by every single user query.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    protected User() {
        // required by JPA
    }

    public User(String email, String passwordHash, String fullName, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    /** Never includes the password hash — this string can end up in a log line. */
    @Override
    public String toString() {
        return "User{id=" + getId() + ", email='" + email + "', status=" + status + "}";
    }
}
