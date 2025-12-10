package com.yego.contractortracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    
    // Datos de Contacto
    @Column(name = "email")
    private String email;
    
    @Column(name = "phone")
    private String phone;
    
    @Column(name = "address")
    private String address;
    
    // Datos Operacionales
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @Column(name = "start_date")
    private LocalDate startDate;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "contract_type")
    private String contractType;
    
    @Column(name = "work_type")
    private String workType; // PART_TIME o FULL_TIME
    
    // Configuración
    @Column(name = "payment_method")
    private String paymentMethod;
    
    @Column(name = "bank_account")
    private String bankAccount;
    
    @Column(name = "commission_rate", precision = 10, scale = 4)
    private BigDecimal commissionRate;
    
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











