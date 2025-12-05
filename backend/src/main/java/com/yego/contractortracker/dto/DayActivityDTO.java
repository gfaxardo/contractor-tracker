package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DayActivityDTO {
    private Integer dayNumber;
    private LocalDate activityDate;
    private Integer tripsDay;
    private Double onlineTime;
    private Boolean connectedFlag;
}




