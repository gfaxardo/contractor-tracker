package com.yego.contractortracker.repository;

import com.yego.contractortracker.entity.ScoutPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScoutPaymentRepository extends JpaRepository<ScoutPayment, Long> {
    List<ScoutPayment> findByScoutId(String scoutId);
    
    List<ScoutPayment> findByStatus(String status);
}











