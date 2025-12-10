package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverWeeklyInfoDTO {
    private String driverId;
    private String driverName;
    private String driverPhone;
    private LocalDate registrationDate;
    private LocalDate hireDate;
    private Boolean hasConnection;
    private Boolean reachedMilestone1;
    private Boolean reachedMilestone5;
    private Boolean reachedMilestone25;
    private LocalDate milestone1Date;
    private LocalDate milestone5Date;
    private LocalDate milestone25Date;
    private Boolean scoutReached8Registrations;
    private Boolean isEligible;
    private String eligibilityReason;
    private BigDecimal amount;
    private String status;
    private Long instanceId;
    
    // Campos individuales por milestone
    private String milestone1Status;
    private BigDecimal milestone1Amount;
    private Long milestone1InstanceId;
    
    private String milestone5Status;
    private BigDecimal milestone5Amount;
    private Long milestone5InstanceId;
    
    private String milestone25Status;
    private BigDecimal milestone25Amount;
    private Long milestone25InstanceId;
    
    // Estados de expiración para milestones no alcanzados
    // Valores posibles: "in_progress" (aún hay tiempo), "expired" (vencido), o null (milestone ya alcanzado)
    private String milestone1ExpirationStatus;
    private String milestone5ExpirationStatus;
    private String milestone25ExpirationStatus;
}


