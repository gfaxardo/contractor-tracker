package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DriverMilestonesDTO {
    private List<MilestoneInstanceDTO> milestones14d;
    private List<MilestoneInstanceDTO> milestones7d;
}












