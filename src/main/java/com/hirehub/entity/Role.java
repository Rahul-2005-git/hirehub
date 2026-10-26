package com.hirehub.entity;

import com.hirehub.entity.enums.RoleName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * Lookup row for a role. Rows are inserted by migration V2 and never by the application,
 * so there is no setter for {@link #name} beyond the constructor used in tests.
 *
 * <p>A table instead of a bare enum column on {@code users}: it gives a real foreign key,
 * lets an admin screen list roles without a redeploy, and leaves room to attach per-role
 * metadata later without touching every user row.</p>
 */
@Entity
@Table(name = "roles")
public class Role extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 20)
    private RoleName name;

    protected Role() {
        // required by JPA
    }

    public Role(RoleName name) {
        this.name = name;
    }

    public RoleName getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Role{id=" + getId() + ", name=" + name + "}";
    }
}
