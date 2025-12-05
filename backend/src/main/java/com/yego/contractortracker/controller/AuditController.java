package com.yego.contractortracker.controller;

import com.yego.contractortracker.entity.AuditLog;
import com.yego.contractortracker.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @GetMapping("/logs")
    public ResponseEntity<Map<String, Object>> getAuditLogs(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String endpoint,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaHasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
            
            Page<AuditLog> auditLogs;
            
            if (username != null && !username.isEmpty()) {
                auditLogs = auditLogRepository.findByUsernameContainingIgnoreCase(username, pageable);
            } else if (endpoint != null && !endpoint.isEmpty()) {
                auditLogs = auditLogRepository.findByEndpointContainingIgnoreCase(endpoint, pageable);
            } else if (method != null && !method.isEmpty()) {
                auditLogs = auditLogRepository.findByMethod(method, pageable);
            } else if (fechaDesde != null || fechaHasta != null) {
                if (fechaDesde != null && fechaHasta != null) {
                    auditLogs = auditLogRepository.findByTimestampBetween(fechaDesde, fechaHasta, pageable);
                } else if (fechaDesde != null) {
                    auditLogs = auditLogRepository.findByTimestampGreaterThanEqual(fechaDesde, pageable);
                } else {
                    auditLogs = auditLogRepository.findByTimestampLessThanEqual(fechaHasta, pageable);
                }
            } else {
                auditLogs = auditLogRepository.findAll(pageable);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("content", auditLogs.getContent());
            response.put("totalElements", auditLogs.getTotalElements());
            response.put("totalPages", auditLogs.getTotalPages());
            response.put("currentPage", auditLogs.getNumber());
            response.put("size", auditLogs.getSize());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error al obtener logs de auditoría: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getAuditStats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaHasta) {
        
        try {
            List<AuditLog> logs;
            
            if (fechaDesde != null && fechaHasta != null) {
                logs = auditLogRepository.findByTimestampBetween(fechaDesde, fechaHasta, Pageable.unpaged()).getContent();
            } else if (fechaDesde != null) {
                logs = auditLogRepository.findByTimestampGreaterThanEqual(fechaDesde, Pageable.unpaged()).getContent();
            } else if (fechaHasta != null) {
                logs = auditLogRepository.findByTimestampLessThanEqual(fechaHasta, Pageable.unpaged()).getContent();
            } else {
                logs = auditLogRepository.findAll();
            }
            
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalLogs", logs.size());
            stats.put("uniqueUsers", logs.stream().map(AuditLog::getUsername).distinct().count());
            stats.put("uniqueEndpoints", logs.stream().map(AuditLog::getEndpoint).distinct().count());
            
            Map<String, Long> methodCounts = new HashMap<>();
            Map<String, Long> statusCounts = new HashMap<>();
            
            for (AuditLog log : logs) {
                methodCounts.merge(log.getMethod(), 1L, Long::sum);
                if (log.getResponseStatus() != null) {
                    String status = log.getResponseStatus().toString();
                    statusCounts.merge(status, 1L, Long::sum);
                }
            }
            
            stats.put("methodCounts", methodCounts);
            stats.put("statusCounts", statusCounts);
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Error al obtener estadísticas: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}





