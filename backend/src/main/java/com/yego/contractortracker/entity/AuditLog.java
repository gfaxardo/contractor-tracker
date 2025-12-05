package com.yego.contractortracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "username")
    private String username;
    
    @Column(name = "action", nullable = false)
    private String action;
    
    @Column(name = "endpoint", nullable = false, length = 500)
    private String endpoint;
    
    @Column(name = "method", nullable = false, length = 10)
    private String method;
    
    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;
    
    @Column(name = "response_status")
    private Integer responseStatus;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;
    
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @PrePersist
    protected void onPersist() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}





