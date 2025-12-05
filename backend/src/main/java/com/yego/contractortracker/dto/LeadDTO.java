package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadDTO {
    private String externalId;
    private String firstName;
    private String lastName;
    private String middleName;
    private String phone;
    private String assetPlateNumber;
    private LocalDate leadCreatedAt;
    private String status;
    private String parkName;
    private String targetCity;
}












