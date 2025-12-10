package com.yego.contractortracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "yango_payment_config", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"milestone_type", "period_days"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class YangoPaymentConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "milestone_type", nullable = false)
    private Integer milestoneType;
    
    @Column(name = "amount_yango", nullable = false)
    private BigDecimal amountYango;
    
    @Column(name = "period_days", nullable = false)
    private Integer periodDays;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
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

