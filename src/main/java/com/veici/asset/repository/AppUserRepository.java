package com.veici.asset.repository;

import com.veici.asset.entity.AppUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @EntityGraph(attributePaths = "roles")
    Optional<AppUser> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT u
        FROM AppUser u
        WHERE u.id = :id
        """)
    Optional<AppUser> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT COUNT(u)
        FROM AppUser u
        WHERE u.enabled = true
          AND EXISTS (
              SELECT role.id
              FROM u.roles role
              WHERE role.code = 'SYSTEM_ADMIN'
          )
        """)
    long countEnabledSystemAdmins();
}