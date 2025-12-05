package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadReprocessConfig {
    private Integer timeMarginDays;
    private Boolean matchByPhone;
    private Boolean matchByName;
    private Double matchThreshold;
    private Double nameSimilarityThreshold;
    private Double phoneSimilarityThreshold;
    private Boolean enableFuzzyMatching;
    private Integer minWordsMatch;
    private Boolean ignoreSecondLastName;
    private String reprocessScope; // "all", "unmatched", "discarded"
}

