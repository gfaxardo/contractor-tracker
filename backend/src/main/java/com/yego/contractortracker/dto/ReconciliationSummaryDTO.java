package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationSummaryDTO {
    private String period;
    private String periodType;
    private TotalsDTO totals;
    private List<ScoutSummaryDTO> byScout;
    private ConversionMetricsDTO conversionMetrics;
    private InconsistenciesDTO inconsistencies;
    private String lastUpdated;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TotalsDTO {
        private Long registrados;
        private Long porCabinet;
        private Long porOtrosMedios;
        private Long conectados;
        private Long conViajes7d;
        private Long conViajes14d;
        private Long conMilestone1;
        private Long conMilestone5;
        private Long conMilestone25;
        private Long conPagoYango;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoutSummaryDTO {
        private String scoutId;
        private String scoutName;
        private Long count;
        private Long registrados;
        private Long conectados;
        private Long conViajes;
        private Long conMilestones;
        private Long conPago;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversionMetricsDTO {
        private Double tasaConexion;
        private Double tasaActivacion;
        private Double tasaMilestone1;
        private Double tasaMilestone5;
        private Double tasaMilestone25;
        private Double tasaPagoYango;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InconsistenciesDTO {
        private Long sinMatch;
        private Long sinPago;
        private Long pagoSinMilestone;
        private Long milestoneSinPago;
    }
}








