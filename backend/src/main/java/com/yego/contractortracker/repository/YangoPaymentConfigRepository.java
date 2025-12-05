package com.yego.contractortracker.repository;

import com.yego.contractortracker.entity.YangoPaymentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface YangoPaymentConfigRepository extends JpaRepository<YangoPaymentConfig, Long> {
    
    Optional<YangoPaymentConfig> findByMilestoneTypeAndPeriodDays(Integer milestoneType, Integer periodDays);
    
    List<YangoPaymentConfig> findByIsActiveTrue();
    
    List<YangoPaymentConfig> findByPeriodDaysAndIsActiveTrue(Integer periodDays);
    
    List<YangoPaymentConfig> findByPeriodDays(Integer periodDays);
}

