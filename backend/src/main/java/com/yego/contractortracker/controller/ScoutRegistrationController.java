package com.yego.contractortracker.controller;

import com.yego.contractortracker.dto.ScoutAffiliationControlDTO;
import com.yego.contractortracker.dto.ScoutAffiliationControlFiltersDTO;
import com.yego.contractortracker.dto.ScoutRegistrationDTO;
import com.yego.contractortracker.service.ScoutRegistrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scout-registrations")
public class ScoutRegistrationController {
    
    @Autowired
    private ScoutRegistrationService registrationService;
    
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadRegistrationsCSV(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (file.isEmpty()) {
                response.put("status", "error");
                response.put("message", "El archivo está vacío");
                return ResponseEntity.badRequest().body(response);
            }
            
            Map<String, Object> result = registrationService.procesarArchivoCSV(file);
            
            response.put("status", "success");
            response.put("data", result);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al procesar archivo: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/control")
    public ResponseEntity<Map<String, Object>> getControlAfiliaciones(
            @RequestParam(value = "scoutId", required = false) String scoutId,
            @RequestParam(value = "weekISO", required = false) String weekISO,
            @RequestParam(value = "fechaInicio", required = false) String fechaInicioStr,
            @RequestParam(value = "fechaFin", required = false) String fechaFinStr,
            @RequestParam(value = "milestoneType", required = false) Integer milestoneType,
            @RequestParam(value = "isMatched", required = false) Boolean isMatched,
            @RequestParam(value = "hasYangoPayment", required = false) Boolean hasYangoPayment,
            @RequestParam(value = "acquisitionMedium", required = false) String acquisitionMedium,
            @RequestParam(value = "driverName", required = false) String driverName,
            @RequestParam(value = "driverPhone", required = false) String driverPhone,
            @RequestParam(value = "amountMin", required = false) String amountMinStr,
            @RequestParam(value = "amountMax", required = false) String amountMaxStr) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            ScoutAffiliationControlFiltersDTO filters = new ScoutAffiliationControlFiltersDTO();
            filters.setScoutId(scoutId);
            filters.setWeekISO(weekISO);
            
            LocalDate fechaInicio = null;
            LocalDate fechaFin = null;
            
            if (fechaInicioStr != null && !fechaInicioStr.isEmpty()) {
                fechaInicio = LocalDate.parse(fechaInicioStr);
            }
            
            if (fechaFinStr != null && !fechaFinStr.isEmpty()) {
                fechaFin = LocalDate.parse(fechaFinStr);
            }
            
            filters.setFechaInicio(fechaInicio);
            filters.setFechaFin(fechaFin);
            filters.setMilestoneType(milestoneType);
            filters.setIsMatched(isMatched);
            filters.setHasYangoPayment(hasYangoPayment);
            filters.setAcquisitionMedium(acquisitionMedium);
            filters.setDriverName(driverName);
            filters.setDriverPhone(driverPhone);
            
            if (amountMinStr != null && !amountMinStr.isEmpty()) {
                try {
                    filters.setAmountMin(new BigDecimal(amountMinStr));
                } catch (NumberFormatException e) {
                    response.put("status", "error");
                    response.put("message", "Formato inválido para amountMin: " + amountMinStr);
                    return ResponseEntity.badRequest().body(response);
                }
            }
            
            if (amountMaxStr != null && !amountMaxStr.isEmpty()) {
                try {
                    filters.setAmountMax(new BigDecimal(amountMaxStr));
                } catch (NumberFormatException e) {
                    response.put("status", "error");
                    response.put("message", "Formato inválido para amountMax: " + amountMaxStr);
                    return ResponseEntity.badRequest().body(response);
                }
            }
            
            List<ScoutAffiliationControlDTO> control = registrationService.obtenerControlAfiliaciones(filters);
            long count = registrationService.contarRegistrosConFiltros(filters);
            
            response.put("status", "success");
            response.put("data", control);
            response.put("count", count);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al obtener control de afiliaciones: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/by-scout/{scoutId}")
    public ResponseEntity<Map<String, Object>> getRegistrosPorScout(
            @PathVariable String scoutId,
            @RequestParam(value = "fechaInicio", required = false) String fechaInicioStr,
            @RequestParam(value = "fechaFin", required = false) String fechaFinStr) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            LocalDate fechaInicio = null;
            LocalDate fechaFin = null;
            
            if (fechaInicioStr != null && !fechaInicioStr.isEmpty()) {
                fechaInicio = LocalDate.parse(fechaInicioStr);
            }
            
            if (fechaFinStr != null && !fechaFinStr.isEmpty()) {
                fechaFin = LocalDate.parse(fechaFinStr);
            }
            
            List<ScoutRegistrationDTO> registros = registrationService.obtenerRegistrosPorScout(scoutId, fechaInicio, fechaFin);
            
            response.put("status", "success");
            response.put("data", registros);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al obtener registros: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/unmatched")
    public ResponseEntity<List<ScoutRegistrationDTO>> getUnmatchedRegistrations() {
        try {
            List<ScoutRegistrationDTO> registros = registrationService.obtenerRegistrosSinMatch();
            return ResponseEntity.ok(registros);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
    
    @PostMapping("/manual-match")
    public ResponseEntity<Map<String, Object>> assignManualMatch(
            @RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Object registrationIdObj = request.get("registrationId");
            String driverId = (String) request.get("driverId");
            
            if (registrationIdObj == null || driverId == null) {
                response.put("status", "error");
                response.put("message", "registrationId y driverId son requeridos");
                return ResponseEntity.badRequest().body(response);
            }
            
            Long registrationId;
            if (registrationIdObj instanceof Number) {
                registrationId = ((Number) registrationIdObj).longValue();
            } else if (registrationIdObj instanceof String) {
                registrationId = Long.parseLong((String) registrationIdObj);
            } else {
                response.put("status", "error");
                response.put("message", "Formato inválido para registrationId");
                return ResponseEntity.badRequest().body(response);
            }
            
            registrationService.asignarMatchManual(registrationId, driverId);
            
            response.put("status", "success");
            response.put("message", "Match asignado manualmente");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al asignar match: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/upload-metadata")
    public ResponseEntity<com.yego.contractortracker.dto.UploadMetadataDTO> getUploadMetadata() {
        try {
            com.yego.contractortracker.dto.UploadMetadataDTO metadata = registrationService.obtenerMetadata();
            return ResponseEntity.ok(metadata);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}

