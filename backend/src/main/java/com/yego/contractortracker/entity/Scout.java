package com.yego.contractortracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "scouts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Scout {
    
    @Id
    @Column(name = "scout_id", nullable = false)
    private String scoutId;
    
    @Column(name = "scout_name", nullable = false, unique = true)
    private String scoutName;
    
    @Column(name = "driver_id")
    private String driverId;
    
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










