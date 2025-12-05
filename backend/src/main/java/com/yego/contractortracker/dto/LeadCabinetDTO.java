package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadCabinetDTO {
    private String externalId;
    private String driverId;
    private LocalDate leadCreatedAt;
    private LocalDate hireDate;
    private Boolean dateMatch;
    private Double matchScore;
    private Boolean isManual;
    private Boolean isDiscarded;
    private String driverFullName;
    private String driverPhone;
    private String leadPhone;
    private String leadFirstName;
    private String leadLastName;
    
    private String driverStatus;
    private Integer totalTrips14d;
    private Long sumWorkTimeSeconds;
    private List<MilestoneInstanceDTO> milestones;
    private List<YangoTransactionMatchedDTO> yangoTransactions14d;
    
    private Long scoutRegistrationId;
    private Double scoutMatchScore;
    private String scoutName;
    private String scoutId;
    private LocalDate scoutRegistrationDate;
}

