package com.yego.contractortracker.repository;

import com.yego.contractortracker.entity.LeadMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LeadMatchRepository extends JpaRepository<LeadMatch, Long> {
    
    Optional<LeadMatch> findByExternalId(String externalId);
    
    List<LeadMatch> findByDriverId(String driverId);
    
    @Query("SELECT lm FROM LeadMatch lm WHERE lm.isDiscarded = false AND (lm.driverId IS NULL OR lm.driverId = '')")
    List<LeadMatch> findUnmatchedLeads();
    
    @Query("SELECT lm FROM LeadMatch lm WHERE lm.isDiscarded = false AND lm.driverId IS NOT NULL")
    List<LeadMatch> findMatchedLeads();
    
    @Query("SELECT lm FROM LeadMatch lm WHERE lm.isDiscarded = false AND lm.leadCreatedAt BETWEEN :startDate AND :endDate")
    List<LeadMatch> findByLeadCreatedAtBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    @Query("SELECT MAX(lm.lastUpdated) FROM LeadMatch lm")
    Optional<java.time.LocalDateTime> findLastUpdated();
}

