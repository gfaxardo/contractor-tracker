package com.yego.contractortracker.repository;

import com.yego.contractortracker.entity.ContractorTrackingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractorTrackingHistoryRepository extends JpaRepository<ContractorTrackingHistory, Long> {
    
    List<ContractorTrackingHistory> findByDriverId(String driverId);
    
    List<ContractorTrackingHistory> findByParkId(String parkId);
    
    @Query("SELECT cth FROM ContractorTrackingHistory cth WHERE cth.driverId = :driverId ORDER BY cth.calculationDate DESC")
    List<ContractorTrackingHistory> findByDriverIdOrderByCalculationDateDesc(@Param("driverId") String driverId);
    
    @Query("SELECT cth FROM ContractorTrackingHistory cth WHERE cth.parkId = :parkId AND cth.calculationDate = (SELECT MAX(cth2.calculationDate) FROM ContractorTrackingHistory cth2 WHERE cth2.driverId = cth.driverId)")
    List<ContractorTrackingHistory> findLatestByParkId(@Param("parkId") String parkId);
    
    @Query("SELECT cth FROM ContractorTrackingHistory cth WHERE cth.driverId = :driverId AND cth.calculationDate = (SELECT MAX(cth2.calculationDate) FROM ContractorTrackingHistory cth2 WHERE cth2.driverId = :driverId)")
    Optional<ContractorTrackingHistory> findLatestByDriverId(@Param("driverId") String driverId);
    
    void deleteByDriverIdAndCalculationDate(String driverId, LocalDateTime calculationDate);
}













