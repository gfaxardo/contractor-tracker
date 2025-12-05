package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnboardingFilterDTO {
    private String parkId;
    private LocalDate startDateFrom;
    private LocalDate startDateTo;
    private String channel;
    private String weekISO;
    private Integer page = 0;
    private Integer size = 50;
}

