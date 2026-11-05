package com.veici.asset.repository;

import com.veici.asset.entity.AppRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

import java.util.Optional;

public interface AppRoleRepository extends JpaRepository<AppRole, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT role
        FROM AppRole role
        WHERE role.code = :code
        """)
    Optional<AppRole> findByCodeForUpdate(@Param("code") String code);

    List<AppRole> findAllByOrderByCodeAsc();
}