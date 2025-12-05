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
public class MilestonePaymentViewDTO {
    // Driver info
    private String driverId;
    private String driverName;
    private String driverPhone;
    private LocalDate hireDate;
    
    // Milestone info
    private Long milestoneInstanceId;
    private Integer milestoneType;
    private Integer periodDays;
    private LocalDateTime fulfillmentDate;
    private Integer tripCount;
    
    // Yango payment info
    private Long yangoTransactionId;
    private BigDecimal amountYango;
    private LocalDateTime yangoPaymentDate;
    private Boolean hasPayment;
    
    // Status
    private String paymentStatus; // "paid", "missing", "pending"
    
    // Lead match info
    private Boolean hasLeadMatch; // Indica si el driver tiene match en lead_matches (cabinet)
}

