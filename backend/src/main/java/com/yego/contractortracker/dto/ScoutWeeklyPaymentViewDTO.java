package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoutWeeklyPaymentViewDTO {
    private String scoutId;
    private String scoutName;
    private List<DriverWeeklyInfoDTO> drivers;
}

