package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class YangoTransactionMatchedDTO {
    private Long id;
    private LocalDateTime transactionDate;
    private Integer milestoneType;
    private BigDecimal amountYango;
    private Long milestoneInstanceId;
    private MilestoneInstanceDTO milestoneInstance;
}








