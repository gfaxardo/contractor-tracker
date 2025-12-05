package com.yego.contractortracker.controller;

import com.yego.contractortracker.dto.YangoTransactionGroup;
import com.yego.contractortracker.entity.YangoTransaction;
import com.yego.contractortracker.service.YangoTransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/yango-transactions")
public class YangoTransactionController {
    
    @Autowired
    private YangoTransactionService transactionService;
    
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadTransactionsCSV(@RequestParam("file") MultipartFile file) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (file.isEmpty()) {
                response.put("status", "error");
                response.put("message", "El archivo está vacío");
                return ResponseEntity.badRequest().body(response);
            }
            
            Map<String, Object> result = transactionService.procesarArchivoCSV(file);
            
            response.put("status", "success");
            response.put("data", result);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al procesar archivo: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping
    public ResponseEntity<Map<String, Object>> getTransactions(
            @RequestParam(required = false) String scoutId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Integer milestoneType,
            @RequestParam(required = false) Boolean isMatched) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<YangoTransaction> transactions = transactionService.obtenerTransaccionesConFiltros(
                    scoutId, dateFrom, dateTo, milestoneType, isMatched);
            
            response.put("status", "success");
            response.put("data", transactions);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al obtener transacciones: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/unmatched")
    public ResponseEntity<Map<String, Object>> getUnmatchedTransactions() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<YangoTransactionGroup> grupos = transactionService.obtenerTransaccionesSinMatchAgrupadas();
            response.put("status", "success");
            response.put("data", grupos);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al obtener transacciones sin match: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/{id}/match")
    public ResponseEntity<Map<String, Object>> assignManualMatch(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String driverId = request.get("driverId");
            
            if (driverId == null) {
                response.put("status", "error");
                response.put("message", "driverId es requerido");
                return ResponseEntity.badRequest().body(response);
            }
            
            transactionService.asignarMatchManual(id, driverId);
            
            response.put("status", "success");
            response.put("message", "Match asignado manualmente");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al asignar match: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/batch-match")
    public ResponseEntity<Map<String, Object>> assignBatchMatch(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            @SuppressWarnings("unchecked")
            List<Object> transactionIdsObj = (List<Object>) request.get("transactionIds");
            String driverId = (String) request.get("driverId");
            @SuppressWarnings("unchecked")
            List<Long> milestoneInstanceIds = request.containsKey("milestoneInstanceIds") ? 
                (List<Long>) request.get("milestoneInstanceIds") : null;
            
            if (transactionIdsObj == null || transactionIdsObj.isEmpty()) {
                response.put("status", "error");
                response.put("message", "transactionIds es requerido");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (driverId == null || driverId.isEmpty()) {
                response.put("status", "error");
                response.put("message", "driverId es requerido");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Convertir transactionIds a Long
            List<Long> transactionIds = transactionIdsObj.stream()
                .map(id -> {
                    if (id instanceof Integer) {
                        return ((Integer) id).longValue();
                    } else if (id instanceof Long) {
                        return (Long) id;
                    } else if (id instanceof Number) {
                        return ((Number) id).longValue();
                    } else {
                        return Long.parseLong(id.toString());
                    }
                })
                .collect(java.util.stream.Collectors.toList());
            
            int matchedCount = transactionService.asignarMatchBatch(transactionIds, driverId, milestoneInstanceIds);
            
            response.put("status", "success");
            response.put("message", String.format("Matcheadas %d transacciones", matchedCount));
            response.put("matchedCount", matchedCount);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al asignar match batch: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/drivers-by-date")
    public ResponseEntity<Map<String, Object>> getDriversByDate(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "parkId", required = false) String parkId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<Map<String, Object>> drivers = transactionService.obtenerDriversPorFecha(date, parkId);
            
            response.put("status", "success");
            response.put("data", drivers);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al obtener drivers: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/reprocess")
    public ResponseEntity<Map<String, Object>> reprocessUnmatchedTransactions() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Map<String, Object> result = transactionService.reprocesarTransaccionesSinMatch();
            
            response.put("status", "success");
            response.put("data", result);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al reprocesar transacciones: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/cleanup-duplicates")
    public ResponseEntity<Map<String, Object>> cleanupDuplicates() {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> result = transactionService.limpiarDuplicados();
            response.put("status", "success");
            response.put("data", result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al limpiar duplicados: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/upload-metadata")
    public ResponseEntity<com.yego.contractortracker.dto.UploadMetadataDTO> getUploadMetadata() {
        try {
            com.yego.contractortracker.dto.UploadMetadataDTO metadata = transactionService.obtenerMetadata();
            return ResponseEntity.ok(metadata);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}

