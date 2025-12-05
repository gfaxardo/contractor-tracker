package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvolutionMetricsDTO {
    private String period;
    private Integer totalDrivers;
    private Integer soloRegistro;
    private Integer conectoSinViajes;
    private Integer activoConViajes;
    private Double tasaRegistroAConexion;
    private Double tasaConexionAViaje;
    private Double tasaAlcanzo1Viaje;
    private Double tasaAlcanzo5Viajes;
    private Double tasaAlcanzo25Viajes;
    private Double promedioDiasRegistroAConexion;
    private Double promedioDiasConexionAViaje;
    private Double promedioDiasPrimerViajeA25Viajes;
    private Integer totalViajes;
    private Double promedioViajesPorActivo;
}




