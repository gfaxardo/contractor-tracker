package com.yego.contractortracker.controller;

import com.yego.contractortracker.service.ScoutReconciliationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/scout-reconciliation")
public class ScoutReconciliationController {
    
    @Autowired
    private ScoutReconciliationService reconciliationService;
    
    @PostMapping("/reconcile")
    public ResponseEntity<Map<String, Object>> reconciliar() {
        try {
            Map<String, Object> resultado = reconciliationService.reconciliarMatches();
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("data", resultado);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @DeleteMapping("/double-match")
    public ResponseEntity<Map<String, Object>> eliminarDobleMatch(
            @RequestParam String scoutId,
            @RequestParam String driverId,
            @RequestParam(required = false, defaultValue = "false") boolean eliminarScoutReg,
            @RequestParam(required = false, defaultValue = "false") boolean eliminarYango) {
        try {
            reconciliationService.eliminarDobleMatches(scoutId, driverId, eliminarScoutReg, eliminarYango);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Doble match eliminado");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}



