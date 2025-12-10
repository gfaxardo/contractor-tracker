package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoutProfileUpdateDTO {
    
    // Datos de Contacto (editables)
    private String email;
    private String phone;
    private String address;
    
    // Datos Operacionales (editables)
    private String notes;
    private LocalDate startDate;
    private String status;
    private String contractType;
    private String workType; // PART_TIME o FULL_TIME
    
    // Configuración (editables)
    private String paymentMethod;
    private String bankAccount;
    private BigDecimal commissionRate;
    
    // Campos básicos también editables
    private Boolean isActive;
}

