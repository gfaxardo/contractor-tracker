package com.yego.contractortracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "scout_payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoutPayment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "scout_id", nullable = false)
    private String scoutId;
    
    @Column(name = "payment_period_start", nullable = false)
    private LocalDate paymentPeriodStart;
    
    @Column(name = "payment_period_end", nullable = false)
    private LocalDate paymentPeriodEnd;
    
    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;
    
    @Column(name = "transactions_count", nullable = false)
    private Integer transactionsCount = 0;
    
    @Column(name = "status", nullable = false)
    private String status = "pending";
    
    @Column(name = "paid_at")
    private LocalDateTime paidAt;
    
    @Column(name = "instance_ids", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Long> instanceIds;
    
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









