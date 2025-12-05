package com.yego.contractortracker.controller;

import com.yego.contractortracker.dto.ReconciliationSummaryDTO;
import com.yego.contractortracker.service.ReconciliationService;
import com.yego.contractortracker.util.WeekISOUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {
    
    @Autowired
    private ReconciliationService reconciliationService;
    
    @GetMapping("/summary")
    public ResponseEntity<?> getReconciliationSummary(
            @RequestParam(value = "periodType", defaultValue = "day") String periodType,
            @RequestParam(value = "dateFrom", required = false) String dateFromStr,
            @RequestParam(value = "dateTo", required = false) String dateToStr,
            @RequestParam(value = "weekISO", required = false) String weekISO,
            @RequestParam(value = "weekISOs", required = false) String weekISOs,
            @RequestParam(value = "parkId", required = false) String parkId,
            @RequestParam(value = "scoutId", required = false) String scoutId,
            @RequestParam(value = "channel", required = false) String channel) {
        
        try {
            LocalDate dateFrom = null;
            LocalDate dateTo = null;
            List<String> weekISOList = null;
            
            if (weekISOs != null && !weekISOs.isEmpty()) {
                weekISOList = java.util.Arrays.asList(weekISOs.split(","));
            } else if (weekISO != null && !weekISO.isEmpty()) {
                weekISOList = java.util.Arrays.asList(weekISO);
            } else {
                if (dateFromStr != null && !dateFromStr.isEmpty()) {
                    dateFrom = LocalDate.parse(dateFromStr);
                }
                if (dateToStr != null && !dateToStr.isEmpty()) {
                    dateTo = LocalDate.parse(dateToStr);
                }
            }
            
            List<ReconciliationSummaryDTO> summaries = reconciliationService.obtenerResumenConsolidado(
                periodType, dateFrom, dateTo, weekISOList, parkId, scoutId, channel);
            
            return ResponseEntity.ok(summaries);
            
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Error al obtener resumen consolidado: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
    
    @GetMapping("/daily-closure")
    public ResponseEntity<?> getDailyClosure(
            @RequestParam(value = "parkId", required = false) String parkId) {
        
        try {
            ReconciliationSummaryDTO closure = reconciliationService.obtenerCierreDiaAnterior(parkId);
            return ResponseEntity.ok(closure);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", "error");
            error.put("message", "Error al obtener cierre del día anterior: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }
}

