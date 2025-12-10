package com.yego.contractortracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadMetadataDTO {
    private LocalDateTime lastUploadDate;
    private LocalDate dataDateFrom;
    private LocalDate dataDateTo;
    private Integer totalRecords;
    private Integer matchedCount;
    private Integer unmatchedCount;
    private SourceDescriptionDTO sourceDescription;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceDescriptionDTO {
        private String title;
        private String source;
        private String url;
        private String details;
    }
}








