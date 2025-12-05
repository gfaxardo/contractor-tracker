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
public class ScoutPaymentInstanceDTO {
    private Long id;
    private String scoutId;
    private String scoutName;
    private String driverId;
    private String driverName;
    private Integer milestoneType;
    private Long milestoneInstanceId;
    private BigDecimal amount;
    private LocalDate registrationDate;
    private LocalDateTime milestoneFulfillmentDate;
    private Boolean eligibilityVerified;
    private String eligibilityReason;
    private String status;
    private Long paymentId;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
}


