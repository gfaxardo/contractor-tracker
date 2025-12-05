package com.yego.contractortracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "lead_matches")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeadMatch {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "external_id", nullable = false, unique = true)
    private String externalId;
    
    @Column(name = "driver_id", nullable = false)
    private String driverId;
    
    @Column(name = "lead_created_at", nullable = false)
    private LocalDate leadCreatedAt;
    
    @Column(name = "lead_phone")
    private String leadPhone;
    
    @Column(name = "lead_first_name")
    private String leadFirstName;
    
    @Column(name = "lead_last_name")
    private String leadLastName;
    
    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;
    
    @Column(name = "match_score", nullable = false)
    private Double matchScore;
    
    @Column(name = "is_manual", nullable = false)
    private Boolean isManual = false;
    
    @Column(name = "is_discarded", nullable = false)
    private Boolean isDiscarded = false;
    
    @Column(name = "matched_at", nullable = false)
    private LocalDateTime matchedAt;
    
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
    
    @Column(name = "scout_registration_id")
    private Long scoutRegistrationId;
    
    @Column(name = "scout_match_score")
    private Double scoutMatchScore;
    
    @Transient
    private LocalDateTime scoutMatchDate;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scout_registration_id", insertable = false, updatable = false)
    private ScoutRegistration scoutRegistration;
    
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
        if (matchedAt == null) {
            matchedAt = LocalDateTime.now();
        }
    }
}

