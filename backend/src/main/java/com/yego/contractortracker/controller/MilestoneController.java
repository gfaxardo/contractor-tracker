package com.yego.contractortracker.controller;

import com.yego.contractortracker.dto.MilestoneInstanceDTO;
import com.yego.contractortracker.service.MilestoneProgressService;
import com.yego.contractortracker.service.MilestoneTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/milestones")
@CrossOrigin(origins = "*")
public class MilestoneController {
    
    @Autowired
    private MilestoneTrackingService milestoneTrackingService;
    
    @Autowired
    private MilestoneProgressService progressService;
    
    @GetMapping("/driver/{driverId}")
    public ResponseEntity<List<MilestoneInstanceDTO>> obtenerInstanciasDriver(
            @PathVariable String driverId) {
        List<MilestoneInstanceDTO> instancias = milestoneTrackingService.obtenerInstanciasDriver(driverId);
        return ResponseEntity.ok(instancias);
    }
    
    @GetMapping("/driver/{driverId}/instance/{milestoneType}/{periodDays}")
    public ResponseEntity<MilestoneInstanceDTO> obtenerDetalleInstancia(
            @PathVariable String driverId,
            @PathVariable Integer milestoneType,
            @PathVariable Integer periodDays) {
        MilestoneInstanceDTO instancia = milestoneTrackingService
            .obtenerDetalleViajesInstancia(driverId, milestoneType, periodDays);
        
        if (instancia == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(instancia);
    }
    
    @GetMapping("/period/{periodDays}")
    public ResponseEntity<Map<String, Object>> obtenerInstanciasPorPeriodo(
            @PathVariable Integer periodDays,
            @RequestParam(required = false) String parkId,
            @RequestParam(required = false) Integer milestoneType,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate hireDateFrom,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate hireDateTo) {
        Map<String, Object> resultado = milestoneTrackingService
            .obtenerInstanciasPorPeriodoConTotales(periodDays, parkId, milestoneType, hireDateFrom, hireDateTo);
        return ResponseEntity.ok(resultado);
    }
    
    @PostMapping("/batch")
    public ResponseEntity<Map<String, List<MilestoneInstanceDTO>>> obtenerInstanciasPorDriverIds(
            @RequestBody List<String> driverIds,
            @RequestParam(required = false, defaultValue = "14") Integer periodDays) {
        Map<String, List<MilestoneInstanceDTO>> instancias = milestoneTrackingService
            .obtenerInstanciasPorDriverIds(driverIds, periodDays);
        return ResponseEntity.ok(instancias);
    }
    
    @PostMapping("/calculate")
    public ResponseEntity<Map<String, Object>> calcularInstancias(
            @RequestParam(required = false) String parkId,
            @RequestParam(required = false, defaultValue = "14") Integer periodDays,
            @RequestParam(required = false) Integer milestoneType,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate hireDateFrom,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate hireDateTo) {
        try {
            if (periodDays == null) {
                periodDays = 14;
            }
            
            if (periodDays != 7 && periodDays != 14) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "El período debe ser 7 o 14 días");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (milestoneType != null && milestoneType != 1 && milestoneType != 5 && milestoneType != 25) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "El tipo de milestone debe ser 1, 5 o 25");
                return ResponseEntity.badRequest().body(response);
            }
            
            String jobId = milestoneTrackingService.calcularInstanciasAsync(parkId, periodDays, milestoneType, hireDateFrom, hireDateTo);
            Map<String, Object> response = new HashMap<>();
            String message = "Cálculo de milestones para " + periodDays + " días";
            if (milestoneType != null) {
                message += " (tipo " + milestoneType + ")";
            }
            if (hireDateFrom != null && hireDateTo != null) {
                message += " para conductores con hire_date entre " + hireDateFrom + " y " + hireDateTo;
            }
            message += " iniciado exitosamente";
            response.put("success", true);
            response.put("message", message);
            response.put("jobId", jobId);
            response.put("periodDays", periodDays);
            if (milestoneType != null) {
                response.put("milestoneType", milestoneType);
            }
            if (hireDateFrom != null) {
                response.put("hireDateFrom", hireDateFrom.toString());
            }
            if (hireDateTo != null) {
                response.put("hireDateTo", hireDateTo.toString());
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MilestoneController.class);
            logger.error("Error al calcular milestones", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error al calcular milestones: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            response.put("error", e.getClass().getName());
            if (e.getCause() != null) {
                response.put("cause", e.getCause().getMessage());
            }
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/progress/{jobId}")
    public ResponseEntity<MilestoneProgressService.ProgressInfo> obtenerProgreso(
            @PathVariable String jobId) {
        MilestoneProgressService.ProgressInfo progress = progressService.getProgress(jobId);
        if (progress == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(progress);
    }
    
    @DeleteMapping("/progress/{jobId}")
    public ResponseEntity<Map<String, Object>> limpiarProgreso(
            @PathVariable String jobId) {
        progressService.clearProgress(jobId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Progreso limpiado");
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/reprocess-all")
    public ResponseEntity<Map<String, Object>> reprocesarTodos(
            @RequestParam(required = false) String parkId,
            @RequestParam(required = false, defaultValue = "false") Boolean limpiarAntes) {
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MilestoneController.class);
        try {
            logger.info("Reprocesamiento de milestones solicitado - parkId: {}, limpiarAntes: {}", parkId, limpiarAntes);
            
            Map<String, Object> response = new HashMap<>();
            java.util.List<String> jobIds = new java.util.ArrayList<>();
            
            if (limpiarAntes != null && limpiarAntes) {
                logger.info("Limpiando milestones existentes...");
                milestoneTrackingService.limpiarMilestones(parkId);
                response.put("limpiado", true);
                logger.info("Milestones limpiados exitosamente");
            }
            
            // Obtener conteo de drivers que se van a procesar para cada período
            int totalDrivers14d = milestoneTrackingService.obtenerConteoDriversConViajes(parkId, 14, null, null);
            int totalDrivers7d = milestoneTrackingService.obtenerConteoDriversConViajes(parkId, 7, null, null);
            
            logger.info("Drivers a procesar - Período 14 días: {}, Período 7 días: {}", totalDrivers14d, totalDrivers7d);
            
            logger.info("Iniciando cálculo de milestones para período de 14 días...");
            String jobId14 = milestoneTrackingService.calcularInstanciasAsync(parkId, 14, null, null, null);
            logger.info("Job ID para 14 días: {}", jobId14);
            
            logger.info("Iniciando cálculo de milestones para período de 7 días...");
            String jobId7 = milestoneTrackingService.calcularInstanciasAsync(parkId, 7, null, null, null);
            logger.info("Job ID para 7 días: {}", jobId7);
            
            jobIds.add(jobId14);
            jobIds.add(jobId7);
            
            response.put("success", true);
            response.put("message", String.format(
                "Reprocesamiento de milestones iniciado para períodos de 7 y 14 días. " +
                "Se procesarán aproximadamente %d drivers (14 días) y %d drivers (7 días) del parkId %s. " +
                "Los cálculos se están ejecutando en segundo plano.",
                totalDrivers14d, totalDrivers7d, parkId != null ? parkId : "default"));
            response.put("jobIds", jobIds);
            response.put("jobId14d", jobId14);
            response.put("jobId7d", jobId7);
            response.put("totalDrivers14d", totalDrivers14d);
            response.put("totalDrivers7d", totalDrivers7d);
            response.put("scope", "Todos los drivers del parkId (sin filtros de fecha)");
            response.put("note", "Los cálculos se están ejecutando en segundo plano. Usa los jobIds para monitorear el progreso.");
            
            logger.info("Reprocesamiento iniciado exitosamente - jobIds: {}, drivers 14d: {}, drivers 7d: {}", 
                    jobIds, totalDrivers14d, totalDrivers7d);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error al reprocesar milestones", e);
            e.printStackTrace();
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error al reprocesar milestones: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            response.put("error", e.getClass().getName());
            if (e.getCause() != null) {
                response.put("cause", e.getCause().getMessage());
            }
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/diagnostic/{driverId}")
    public ResponseEntity<Map<String, Object>> diagnosticarDriver(
            @PathVariable String driverId,
            @RequestParam(required = false, defaultValue = "14") Integer periodDays) {
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MilestoneController.class);
        
        try {
            Map<String, Object> diagnostic = new HashMap<>();
            
            // Obtener milestones guardados en la base de datos
            List<MilestoneInstanceDTO> milestonesGuardados = milestoneTrackingService.obtenerInstanciasDriver(driverId);
            List<MilestoneInstanceDTO> milestonesDelPeriodo = milestonesGuardados.stream()
                    .filter(m -> m.getPeriodDays().equals(periodDays))
                    .collect(java.util.stream.Collectors.toList());
            
            diagnostic.put("driverId", driverId);
            diagnostic.put("periodDays", periodDays);
            diagnostic.put("milestonesGuardados", milestonesDelPeriodo);
            diagnostic.put("totalMilestonesGuardados", milestonesDelPeriodo.size());
            
            // Obtener viajes desde summary_daily para el período usando el servicio
            try {
                int totalViajes = milestoneTrackingService.obtenerTotalViajesDriver(driverId, periodDays);
                diagnostic.put("totalViajesDesdeSummaryDaily", totalViajes);
                
                // Calcular qué milestones debería tener
                java.util.List<Integer> milestonesEsperados = new java.util.ArrayList<>();
                if (totalViajes >= 1) milestonesEsperados.add(1);
                if (totalViajes >= 5) milestonesEsperados.add(5);
                if (totalViajes >= 25) milestonesEsperados.add(25);
                diagnostic.put("milestonesEsperados", milestonesEsperados);
                
                // Comparar con los guardados
                java.util.Set<Integer> milestonesGuardadosSet = milestonesDelPeriodo.stream()
                        .map(MilestoneInstanceDTO::getMilestoneType)
                        .collect(java.util.stream.Collectors.toSet());
                java.util.Set<Integer> milestonesEsperadosSet = new java.util.HashSet<>(milestonesEsperados);
                
                diagnostic.put("milestonesCoinciden", milestonesGuardadosSet.equals(milestonesEsperadosSet));
                
                // Calcular milestones faltantes
                java.util.Set<Integer> milestonesFaltantes = new java.util.HashSet<>(milestonesEsperadosSet);
                milestonesFaltantes.removeAll(milestonesGuardadosSet);
                diagnostic.put("milestonesFaltantes", new java.util.ArrayList<>(milestonesFaltantes));
            } catch (Exception e) {
                logger.warn("Error al obtener viajes desde summary_daily: {}", e.getMessage());
                diagnostic.put("errorViajes", e.getMessage());
            }
            
            return ResponseEntity.ok(diagnostic);
        } catch (Exception e) {
            logger.error("Error al diagnosticar driver", e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            error.put("driverId", driverId);
            return ResponseEntity.status(500).body(error);
        }
    }
}
