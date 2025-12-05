package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadMatchDTO {
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
}

