package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoutAffiliationControlFiltersDTO {
    private String scoutId;
    private String weekISO; // formato "YYYY-WNN"
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Integer milestoneType; // filtro por tipo de milestone alcanzado
    private Boolean isMatched; // filtro por estado de match
    private Boolean hasYangoPayment; // filtro por estado de pago Yango
    private String acquisitionMedium; // FLEET o CABINET
    private String driverName; // búsqueda por nombre (LIKE)
    private String driverPhone; // búsqueda por teléfono
    private BigDecimal amountMin; // monto mínimo de transacción
    private BigDecimal amountMax; // monto máximo de transacción
}









