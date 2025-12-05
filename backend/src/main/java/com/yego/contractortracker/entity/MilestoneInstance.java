package com.yego.contractortracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "milestone_instances")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneInstance {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "driver_id", nullable = false)
    private String driverId;
    
    @Column(name = "park_id", nullable = false)
    private String parkId;
    
    @Column(name = "milestone_type", nullable = false)
    private Integer milestoneType;
    
    @Column(name = "period_days", nullable = false)
    private Integer periodDays;
    
    @Column(name = "fulfillment_date", nullable = false)
    private LocalDateTime fulfillmentDate;
    
    @Column(name = "calculation_date", nullable = false)
    private LocalDateTime calculationDate;
    
    @Column(name = "trip_count", nullable = false)
    private Integer tripCount = 0;
    
    @Column(name = "trip_details", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> tripDetails;
    
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
    
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        lastUpdated = LocalDateTime.now();
        if (calculationDate == null) {
            calculationDate = LocalDateTime.now();
        }
    }
}













