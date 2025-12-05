package com.yego.contractortracker.repository;

import com.yego.contractortracker.entity.ScoutPaymentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScoutPaymentConfigRepository extends JpaRepository<ScoutPaymentConfig, Long> {
    Optional<ScoutPaymentConfig> findByMilestoneType(Integer milestoneType);
    
    List<ScoutPaymentConfig> findByIsActiveTrue();
}

