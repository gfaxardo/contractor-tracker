package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneInstanceDTO {
    private Long id;
    private String driverId;
    private String parkId;
    private Integer milestoneType;
    private Integer periodDays;
    private LocalDateTime fulfillmentDate;
    private LocalDateTime calculationDate;
    private Integer tripCount;
    private List<MilestoneTripDetailDTO> tripDetails;
}












