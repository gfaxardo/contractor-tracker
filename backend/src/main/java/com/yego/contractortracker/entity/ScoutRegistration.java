package com.yego.contractortracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "scout_registrations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoutRegistration {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "scout_id", nullable = false)
    private String scoutId;
    
    @Column(name = "registration_date", nullable = false)
    private LocalDate registrationDate;
    
    @Column(name = "driver_license")
    private String driverLicense;
    
    @Column(name = "driver_name", nullable = false)
    private String driverName;
    
    @Column(name = "driver_phone")
    private String driverPhone;
    
    @Column(name = "acquisition_medium")
    private String acquisitionMedium;
    
    @Column(name = "driver_id")
    private String driverId;
    
    @Column(name = "match_score")
    private Double matchScore;
    
    @Column(name = "is_matched", nullable = false)
    private Boolean isMatched = false;
    
    @Transient
    private String matchSource;
    
    @Transient
    private String reconciliationStatus;
    
    @Transient
    private Long yangoTransactionId;
    
    @Transient
    private Boolean isReconciled = false;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
    
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

