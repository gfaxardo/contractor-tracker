package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadProcessingResultDTO {
    private Integer totalLeads;
    private Integer matchedCount;
    private Integer unmatchedCount;
    private Integer discardedCount;
    private LocalDateTime lastUpdated;
    private String message;
    private LocalDate dataDateFrom;
    private LocalDate dataDateTo;
}






