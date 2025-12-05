package com.yego.contractortracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "yango_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class YangoTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;
    
    @Column(name = "scout_id", nullable = false)
    private String scoutId;
    
    @Column(name = "driver_id")
    private String driverId;
    
    @Column(name = "driver_name_from_comment")
    private String driverNameFromComment;
    
    @Column(name = "milestone_type")
    private Integer milestoneType;
    
    @Column(name = "amount_yango", nullable = false)
    private BigDecimal amountYango;
    
    @Column(name = "amount_indicator")
    private Integer amountIndicator = 1;
    
    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;
    
    @Column(name = "category_id")
    private String categoryId;
    
    @Column(name = "category")
    private String category;
    
    @Column(name = "document")
    private String document;
    
    @Column(name = "initiated_by")
    private String initiatedBy;
    
    @Column(name = "milestone_instance_id")
    private Long milestoneInstanceId;
    
    @Column(name = "match_confidence")
    private BigDecimal matchConfidence;
    
    @Column(name = "is_matched", nullable = false)
    private Boolean isMatched = false;
    
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










