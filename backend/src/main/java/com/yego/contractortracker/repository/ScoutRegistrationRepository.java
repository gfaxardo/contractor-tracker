package com.yego.contractortracker.repository;

import com.yego.contractortracker.entity.ScoutRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ScoutRegistrationRepository extends JpaRepository<ScoutRegistration, Long> {
    List<ScoutRegistration> findByScoutId(String scoutId);
    List<ScoutRegistration> findByScoutIdAndRegistrationDateBetween(String scoutId, LocalDate fechaInicio, LocalDate fechaFin);
    List<ScoutRegistration> findByRegistrationDateBetween(LocalDate fechaInicio, LocalDate fechaFin);
    List<ScoutRegistration> findByDriverId(String driverId);
    List<ScoutRegistration> findByIsMatched(Boolean isMatched);
    List<ScoutRegistration> findByScoutIdAndDriverId(String scoutId, String driverId);
    List<ScoutRegistration> findByScoutIdAndDriverIdAndRegistrationDateBetween(String scoutId, String driverId, LocalDate fechaInicio, LocalDate fechaFin);
    List<ScoutRegistration> findByMatchSource(String matchSource);
    List<ScoutRegistration> findByReconciliationStatus(String reconciliationStatus);
}

