package com.yego.contractortracker.repository;

import com.yego.contractortracker.entity.MilestoneInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MilestoneInstanceRepository extends JpaRepository<MilestoneInstance, Long> {
    
    List<MilestoneInstance> findByDriverId(String driverId);
    
    List<MilestoneInstance> findByDriverIdAndPeriodDays(String driverId, Integer periodDays);
    
    Optional<MilestoneInstance> findByDriverIdAndMilestoneTypeAndPeriodDays(
        String driverId, 
        Integer milestoneType, 
        Integer periodDays
    );
    
    List<MilestoneInstance> findByMilestoneTypeAndPeriodDays(Integer milestoneType, Integer periodDays);
    
    List<MilestoneInstance> findByParkIdAndMilestoneTypeAndPeriodDays(
        String parkId, 
        Integer milestoneType, 
        Integer periodDays
    );
    
    @Query("SELECT mi FROM MilestoneInstance mi WHERE mi.driverId = :driverId ORDER BY mi.milestoneType ASC, mi.periodDays ASC")
    List<MilestoneInstance> findByDriverIdOrdered(@Param("driverId") String driverId);
    
    @Query("SELECT mi FROM MilestoneInstance mi WHERE mi.parkId = :parkId AND mi.milestoneType = :milestoneType AND mi.periodDays = :periodDays ORDER BY mi.fulfillmentDate DESC")
    List<MilestoneInstance> findByParkIdAndMilestoneTypeAndPeriodDaysOrdered(
        @Param("parkId") String parkId,
        @Param("milestoneType") Integer milestoneType,
        @Param("periodDays") Integer periodDays
    );
    
    void deleteByDriverIdAndMilestoneTypeAndPeriodDays(String driverId, Integer milestoneType, Integer periodDays);
    
    List<MilestoneInstance> findByDriverIdInAndPeriodDays(java.util.Set<String> driverIds, Integer periodDays);
    
    List<MilestoneInstance> findByDriverIdIn(java.util.Set<String> driverIds);
}



