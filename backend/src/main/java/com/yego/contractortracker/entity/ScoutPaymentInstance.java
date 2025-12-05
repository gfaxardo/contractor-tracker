package com.yego.contractortracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "scout_payment_instances")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoutPaymentInstance {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "scout_id", nullable = false)
    private String scoutId;
    
    @Column(name = "driver_id", nullable = false)
    private String driverId;
    
    @Column(name = "milestone_type", nullable = false)
    private Integer milestoneType;
    
    @Column(name = "milestone_instance_id")
    private Long milestoneInstanceId;
    
    @Column(name = "amount", nullable = false)
    private BigDecimal amount;
    
    @Column(name = "registration_date", nullable = false)
    private LocalDate registrationDate;
    
    @Column(name = "milestone_fulfillment_date", nullable = false)
    private LocalDateTime milestoneFulfillmentDate;
    
    @Column(name = "eligibility_verified", nullable = false)
    private Boolean eligibilityVerified = false;
    
    @Column(name = "eligibility_reason", columnDefinition = "TEXT")
    private String eligibilityReason;
    
    @Column(name = "status", nullable = false)
    private String status = "pending";
    
    @Column(name = "payment_id")
    private Long paymentId;
    
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


