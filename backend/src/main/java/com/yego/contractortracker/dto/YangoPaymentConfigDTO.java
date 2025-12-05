package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class YangoPaymentConfigDTO {
    private Long id;
    private Integer milestoneType;
    private BigDecimal amountYango;
    private Integer periodDays;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdated;
}

