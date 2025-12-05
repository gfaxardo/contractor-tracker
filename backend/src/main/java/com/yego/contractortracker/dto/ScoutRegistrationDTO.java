package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoutRegistrationDTO {
    private Long id;
    private String scoutId;
    private String scoutName;
    private LocalDate registrationDate;
    private String driverLicense;
    private String driverName;
    private String driverPhone;
    private String acquisitionMedium;
    private String driverId;
    private Double matchScore;
    private Boolean isMatched;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
}

