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
public class ScoutAffiliationControlDTO {
    private Long registrationId;
    private String scoutId;
    private String scoutName;
    private LocalDate registrationDate;
    private String driverLicense;
    private String driverName;
    private String driverPhone;
    private String acquisitionMedium;
    private String driverId;
    private Boolean isMatched;
    private Double matchScore;
    
    private Integer milestoneType7d;
    private Integer tripCount7d;
    private LocalDateTime milestoneFulfillmentDate7d;
    
    private Boolean hasYangoPayment;
    private BigDecimal yangoPaymentAmount;
    private LocalDateTime yangoPaymentDate;
    private Long yangoTransactionId;
}

