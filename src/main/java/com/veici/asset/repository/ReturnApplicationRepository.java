package com.veici.asset.repository;

import com.veici.asset.entity.ReturnApplication;
import com.veici.asset.entity.ReturnStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReturnApplicationRepository
        extends JpaRepository<ReturnApplication, Long> {

    boolean existsByLoan_IdAndStatus(Long loanId, ReturnStatus status);

    @EntityGraph(attributePaths = {"loan", "loan.asset"})
    Page<ReturnApplication> findByStatus(
            ReturnStatus status, Pageable pageable);

    // 先查编号，避免在加锁前加载实体。
    @Query("""
        SELECT r.loan.id
        FROM ReturnApplication r
        WHERE r.id = :id
        """)
    Optional<Long> findLoanIdByReturnId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r
        FROM ReturnApplication r
        WHERE r.id = :id
        """)
    Optional<ReturnApplication> findByIdForUpdate(@Param("id") Long id);

    Optional<ReturnApplication> findByLoan_IdAndStatus(
            Long loanId, ReturnStatus status);
}