package com.yego.contractortracker.repository;

import com.yego.contractortracker.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    Page<AuditLog> findByUsernameContainingIgnoreCase(String username, Pageable pageable);
    Page<AuditLog> findByEndpointContainingIgnoreCase(String endpoint, Pageable pageable);
    Page<AuditLog> findByMethod(String method, Pageable pageable);
    Page<AuditLog> findByTimestampBetween(LocalDateTime fechaDesde, LocalDateTime fechaHasta, Pageable pageable);
    Page<AuditLog> findByTimestampGreaterThanEqual(LocalDateTime fechaDesde, Pageable pageable);
    Page<AuditLog> findByTimestampLessThanEqual(LocalDateTime fechaHasta, Pageable pageable);
}

