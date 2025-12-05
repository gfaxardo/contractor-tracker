package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverOnboardingDTO {
    private String driverId;
    private String parkId;
    private String channel;
    private String fullName;
    private String phone;
    private LocalDate startDate;
    private String licenseNumber;
    private String status14d;
    private Integer totalTrips14d;
    private Double totalOnlineTime14d;
    private Long sumWorkTimeSeconds;
    private Boolean hasHistoricalConnection;
    private LocalDate leadCreatedAt;
    private Double matchScore;
    private String scoutId;
    private String scoutName;
    private LocalDate scoutRegistrationDate;
    private Double scoutMatchScore;
    private Boolean hasScoutRegistration;
    private Integer diasRegistroAConexion;
    private Integer diasConexionAViaje;
    private Integer diasPrimerViajeA25Viajes;
    private LocalDate primeraConexionDate;
    private LocalDate primerViajeDate;
    private Integer diasActivos;
    private Integer diasConectados;
    private Double tasaConversionConexion;
    private Boolean tieneLead;
    private Boolean tieneScout;
    private Boolean matchScoreBajo;
    private Boolean matchManual;
    private Boolean tieneInconsistencias;
    private String diaSemanaRegistro;
    private Integer semanaMesRegistro;
    private String semanaISORegistro;
    private List<DayActivityDTO> days;
    private List<MilestoneInstanceDTO> milestones14d;
    private List<MilestoneInstanceDTO> milestones7d;
    private List<YangoTransactionMatchedDTO> yangoTransactions14d;
}

