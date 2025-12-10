package com.yego.contractortracker.repository;

import com.yego.contractortracker.entity.ScoutPaymentInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScoutPaymentInstanceRepository extends JpaRepository<ScoutPaymentInstance, Long> {
    
    List<ScoutPaymentInstance> findByScoutId(String scoutId);
    
    List<ScoutPaymentInstance> findByScoutIdAndStatus(String scoutId, String status);
    
    List<ScoutPaymentInstance> findByScoutIdAndStatusOrderByRegistrationDateDesc(String scoutId, String status);
    
    List<ScoutPaymentInstance> findByDriverId(String driverId);
    
    List<ScoutPaymentInstance> findByMilestoneInstanceId(Long milestoneInstanceId);
    
    List<ScoutPaymentInstance> findByPaymentId(Long paymentId);
    
    @Query("SELECT spi FROM ScoutPaymentInstance spi WHERE spi.scoutId = :scoutId AND spi.status = :status AND spi.registrationDate BETWEEN :dateFrom AND :dateTo ORDER BY spi.registrationDate DESC")
    List<ScoutPaymentInstance> findByScoutIdAndStatusAndRegistrationDateBetween(
        @Param("scoutId") String scoutId,
        @Param("status") String status,
        @Param("dateFrom") LocalDate dateFrom,
        @Param("dateTo") LocalDate dateTo
    );
    
    @Query("SELECT spi FROM ScoutPaymentInstance spi WHERE spi.scoutId = :scoutId AND spi.driverId = :driverId AND spi.milestoneType = :milestoneType")
    Optional<ScoutPaymentInstance> findByScoutIdAndDriverIdAndMilestoneType(
        @Param("scoutId") String scoutId,
        @Param("driverId") String driverId,
        @Param("milestoneType") Integer milestoneType
    );
    
    @Query("SELECT COUNT(spi) FROM ScoutPaymentInstance spi WHERE spi.scoutId = :scoutId AND spi.status = 'pending'")
    Long countPendingByScoutId(@Param("scoutId") String scoutId);
}



