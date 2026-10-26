package com.hirehub.repository;

import com.hirehub.entity.Role;
import com.hirehub.entity.enums.RoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Roles are reference data seeded by migration V2 — this repository only ever reads them.
 */
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(RoleName name);
}
