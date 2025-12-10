package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoutProfileDTO {
    
    // Información básica
    private String scoutId;
    private String scoutName;
    private String driverId;
    private Boolean isActive;
    
    // Datos de Contacto
    private String email;
    private String phone;
    private String address;
    
    // Datos Operacionales
    private String notes;
    private LocalDate startDate;
    private String status;
    private String contractType;
    private String workType; // PART_TIME o FULL_TIME
    
    // Configuración
    private String paymentMethod;
    private String bankAccount;
    private BigDecimal commissionRate;
    
    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
    
    // Métricas Calculadas (solo lectura, derivadas de la ingesta)
    private Long totalRegistrations;
    private Long matchedRegistrations;
    private Long totalDriversAffiliated;
    private LocalDate lastRegistrationDate;
}

