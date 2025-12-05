package com.yego.contractortracker.controller;

import com.yego.contractortracker.dto.*;
import com.yego.contractortracker.service.LeadProcessingService;
import com.yego.contractortracker.util.WeekISOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leads")
public class LeadController {
    
    @Autowired
    private LeadProcessingService leadProcessingService;
    
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadLeadsCSV(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (file.isEmpty()) {
                response.put("status", "error");
                response.put("message", "El archivo está vacío");
                return ResponseEntity.badRequest().body(response);
            }
            
            LeadProcessingResultDTO result = leadProcessingService.procesarArchivoCSV(file);
            
            response.put("status", "success");
            response.put("data", result);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al procesar archivo: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/unmatched")
    public ResponseEntity<List<LeadMatchDTO>> getUnmatchedLeads() {
        try {
            List<LeadMatchDTO> leads = leadProcessingService.obtenerLeadsSinMatch();
            return ResponseEntity.ok(leads);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
    
    @PostMapping("/manual-match")
    public ResponseEntity<Map<String, Object>> assignManualMatch(
            @RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String externalId = request.get("externalId");
            String driverId = request.get("driverId");
            
            if (externalId == null || driverId == null) {
                response.put("status", "error");
                response.put("message", "externalId y driverId son requeridos");
                return ResponseEntity.badRequest().body(response);
            }
            
            leadProcessingService.asignarMatchManual(externalId, driverId);
            
            response.put("status", "success");
            response.put("message", "Match asignado manualmente");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al asignar match: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/discard")
    public ResponseEntity<Map<String, Object>> discardLead(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String externalId = request.get("externalId");
            
            if (externalId == null) {
                response.put("status", "error");
                response.put("message", "externalId es requerido");
                return ResponseEntity.badRequest().body(response);
            }
            
            leadProcessingService.descartarLead(externalId);
            
            response.put("status", "success");
            response.put("message", "Lead descartado");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al descartar lead: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/reprocess")
    public ResponseEntity<Map<String, Object>> reprocessLeads(@RequestBody LeadReprocessConfig config) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            LeadProcessingResultDTO result = leadProcessingService.reprocesarConReglas(config);
            
            response.put("status", "success");
            response.put("data", result);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al reprocesar: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/processing-status")
    public ResponseEntity<Map<String, Object>> getProcessingStatus() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            LocalDateTime lastUpdated = leadProcessingService.obtenerUltimaActualizacion();
            
            response.put("lastUpdated", lastUpdated);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al obtener estado: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/upload-metadata")
    public ResponseEntity<UploadMetadataDTO> getUploadMetadata() {
        try {
            UploadMetadataDTO metadata = leadProcessingService.obtenerMetadata();
            return ResponseEntity.ok(metadata);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
    
    @GetMapping("/drivers-by-date")
    public ResponseEntity<Map<String, Object>> getDriversByDate(
            @RequestParam("date") String dateStr,
            @RequestParam(value = "parkId", required = false) String parkId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            LocalDate date = LocalDate.parse(dateStr);
            List<Map<String, Object>> drivers = leadProcessingService.obtenerDriversPorFecha(date, parkId);
            
            response.put("status", "success");
            response.put("data", drivers);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al obtener drivers: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/cabinet")
    public ResponseEntity<?> getLeadsCabinet(
            @RequestParam(value = "dateFrom", required = false) String dateFromStr,
            @RequestParam(value = "dateTo", required = false) String dateToStr,
            @RequestParam(value = "weekISO", required = false) String weekISO,
            @RequestParam(value = "matchStatus", required = false) String matchStatus,
            @RequestParam(value = "driverStatus", required = false) String driverStatus,
            @RequestParam(value = "milestoneType", required = false) Integer milestoneType,
            @RequestParam(value = "milestonePeriod", required = false) Integer milestonePeriod,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "includeDiscarded", required = false) Boolean includeDiscarded) {
        
        try {
            LocalDate dateFrom = null;
            LocalDate dateTo = null;
            
            if (weekISO != null && !weekISO.isEmpty()) {
                try {
                    LocalDate[] weekRange = WeekISOUtil.getWeekRange(weekISO);
                    if (weekRange != null && weekRange.length == 2) {
                        dateFrom = weekRange[0];
                        dateTo = weekRange[1];
                    }
                } catch (Exception e) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("status", "error");
                    error.put("message", "Error al calcular semana ISO: " + e.getMessage());
                    return ResponseEntity.badRequest().body(error);
                }
            } else {
                dateFrom = dateFromStr != null && !dateFromStr.isEmpty() ? LocalDate.parse(dateFromStr) : null;
                dateTo = dateToStr != null && !dateToStr.isEmpty() ? LocalDate.parse(dateToStr) : null;
            }
            
            List<LeadCabinetDTO> leads = leadProcessingService.obtenerTodosLosLeadsConEstado(
                    dateFrom, dateTo, matchStatus, driverStatus, milestoneType, 
                    milestonePeriod, search, includeDiscarded);
            
            return ResponseEntity.ok(leads);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Error al obtener leads para cabinet: " + e.getMessage());
            error.put("type", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    @GetMapping("/{externalId}/scout-suggestions")
    public ResponseEntity<Map<String, Object>> getScoutSuggestions(@PathVariable String externalId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<Map<String, Object>> suggestions = leadProcessingService.obtenerSugerenciasScout(externalId);
            
            response.put("status", "success");
            response.put("data", suggestions);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al obtener sugerencias: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/{externalId}/assign-scout")
    public ResponseEntity<Map<String, Object>> assignScoutRegistration(
            @PathVariable String externalId,
            @RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Object scoutRegistrationIdObj = request.get("scoutRegistrationId");
            if (scoutRegistrationIdObj == null) {
                response.put("status", "error");
                response.put("message", "scoutRegistrationId es requerido");
                return ResponseEntity.badRequest().body(response);
            }
            
            Long scoutRegistrationId;
            if (scoutRegistrationIdObj instanceof Number) {
                scoutRegistrationId = ((Number) scoutRegistrationIdObj).longValue();
            } else {
                scoutRegistrationId = Long.parseLong(scoutRegistrationIdObj.toString());
            }
            
            leadProcessingService.asignarScoutRegistration(externalId, scoutRegistrationId);
            
            response.put("status", "success");
            response.put("message", "Scout registration asignado exitosamente");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al asignar scout registration: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}

