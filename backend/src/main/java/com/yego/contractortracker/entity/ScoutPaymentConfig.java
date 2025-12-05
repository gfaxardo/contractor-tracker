package com.yego.contractortracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "scout_payment_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoutPaymentConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "milestone_type", nullable = false, unique = true)
    private Integer milestoneType;
    
    @Column(name = "amount_scout", nullable = false)
    private BigDecimal amountScout;
    
    @Column(name = "payment_days", nullable = false)
    private Integer paymentDays = 7;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "min_registrations_required")
    private Integer minRegistrationsRequired = 8;
    
    @Column(name = "min_connection_seconds")
    private Integer minConnectionSeconds = 1;
    
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









