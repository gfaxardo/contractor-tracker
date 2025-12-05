package com.yego.contractortracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "contractor_tracking_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContractorTrackingHistory {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "driver_id", nullable = false)
    private String driverId;
    
    @Column(name = "park_id", nullable = false)
    private String parkId;
    
    @Column(name = "calculation_date", nullable = false)
    private LocalDateTime calculationDate;
    
    @Column(name = "total_trips_historical", nullable = false)
    private Integer totalTripsHistorical = 0;
    
    @Column(name = "sum_work_time_seconds")
    private Long sumWorkTimeSeconds;
    
    @Column(name = "has_historical_connection", nullable = false)
    private Boolean hasHistoricalConnection = false;
    
    @Column(name = "status_registered", nullable = false)
    private Boolean statusRegistered = false;
    
    @Column(name = "status_connected", nullable = false)
    private Boolean statusConnected = false;
    
    @Column(name = "status_with_trips", nullable = false)
    private Boolean statusWithTrips = false;
    
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
    
    @Column(name = "acquisition_channel")
    private String acquisitionChannel;
    
    @Column(name = "primera_conexion_date")
    private LocalDate primeraConexionDate;
    
    @Column(name = "primer_viaje_date")
    private LocalDate primerViajeDate;
    
    @Column(name = "dias_activos")
    private Integer diasActivos;
    
    @Column(name = "dias_conectados")
    private Integer diasConectados;
    
    @Column(name = "dias_registro_a_conexion")
    private Integer diasRegistroAConexion;
    
    @Column(name = "dias_conexion_a_viaje")
    private Integer diasConexionAViaje;
    
    @Column(name = "dias_primer_viaje_a_25_viajes")
    private Integer diasPrimerViajeA25Viajes;
    
    @Column(name = "tasa_conversion_conexion")
    private Double tasaConversionConexion;
    
    @Column(name = "tiene_lead")
    private Boolean tieneLead;
    
    @Column(name = "tiene_scout")
    private Boolean tieneScout;
    
    @Column(name = "match_score_bajo")
    private Boolean matchScoreBajo;
    
    @Column(name = "tiene_inconsistencias")
    private Boolean tieneInconsistencias;
    
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
        if (calculationDate == null) {
            calculationDate = LocalDateTime.now();
        }
    }
}


