package com.yego.contractortracker.controller;

import com.yego.contractortracker.dto.ScoutPaymentInstanceDTO;
import com.yego.contractortracker.dto.ScoutWeeklyPaymentViewDTO;
import com.yego.contractortracker.entity.ScoutPayment;
import com.yego.contractortracker.entity.ScoutPaymentConfig;
import com.yego.contractortracker.service.ScoutPaymentInstanceService;
import com.yego.contractortracker.service.ScoutPaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scout-payments")
public class ScoutPaymentController {
    
    @Autowired
    private ScoutPaymentService paymentService;
    
    @Autowired
    private ScoutPaymentInstanceService instanceService;
    
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getPaymentConfig() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<ScoutPaymentConfig> configs = paymentService.obtenerConfiguracion();
            response.put("status", "success");
            response.put("data", configs);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al obtener configuración: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PutMapping("/config/{id}")
    public ResponseEntity<Map<String, Object>> updatePaymentConfig(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            BigDecimal amountScout = request.get("amountScout") != null ? 
                    new BigDecimal(request.get("amountScout").toString()) : null;
            Integer paymentDays = request.get("paymentDays") != null ? 
                    Integer.parseInt(request.get("paymentDays").toString()) : null;
            Boolean isActive = request.get("isActive") != null ? 
                    Boolean.parseBoolean(request.get("isActive").toString()) : null;
            Integer minRegistrationsRequired = request.get("minRegistrationsRequired") != null ? 
                    Integer.parseInt(request.get("minRegistrationsRequired").toString()) : null;
            Integer minConnectionSeconds = request.get("minConnectionSeconds") != null ? 
                    Integer.parseInt(request.get("minConnectionSeconds").toString()) : null;
            
            ScoutPaymentConfig config = paymentService.actualizarConfiguracion(id, amountScout, paymentDays, isActive, minRegistrationsRequired, minConnectionSeconds);
            
            response.put("status", "success");
            response.put("data", config);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al actualizar configuración: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/calculate")
    public ResponseEntity<Map<String, Object>> calculateLiquidation(
            @RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String scoutId = (String) request.get("scoutId");
            String fechaInicioStr = (String) request.get("fechaInicio");
            String fechaFinStr = (String) request.get("fechaFin");
            
            if (scoutId == null || fechaInicioStr == null || fechaFinStr == null) {
                response.put("status", "error");
                response.put("message", "scoutId, fechaInicio y fechaFin son requeridos");
                return ResponseEntity.badRequest().body(response);
            }
            
            LocalDate fechaInicio = LocalDate.parse(fechaInicioStr);
            LocalDate fechaFin = LocalDate.parse(fechaFinStr);
            
            Map<String, Object> calculo = paymentService.calcularLiquidacionSemanal(scoutId, fechaInicio, fechaFin);
            
            response.put("status", "success");
            response.put("data", calculo);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al calcular liquidación: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generatePayment(
            @RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String scoutId = (String) request.get("scoutId");
            String fechaInicioStr = (String) request.get("fechaInicio");
            String fechaFinStr = (String) request.get("fechaFin");
            
            if (scoutId == null || fechaInicioStr == null || fechaFinStr == null) {
                response.put("status", "error");
                response.put("message", "scoutId, fechaInicio y fechaFin son requeridos");
                return ResponseEntity.badRequest().body(response);
            }
            
            LocalDate fechaInicio = LocalDate.parse(fechaInicioStr);
            LocalDate fechaFin = LocalDate.parse(fechaFinStr);
            
            ScoutPayment pago = paymentService.generarPagoScout(scoutId, fechaInicio, fechaFin);
            
            response.put("status", "success");
            response.put("data", pago);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al generar pago: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping
    public ResponseEntity<Map<String, Object>> getPayments(
            @RequestParam(required = false) String scoutId,
            @RequestParam(required = false) String status) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (scoutId == null || scoutId.trim().isEmpty()) {
                response.put("status", "error");
                response.put("message", "scoutId es requerido");
                return ResponseEntity.badRequest().body(response);
            }
            
            List<ScoutPayment> payments = paymentService.obtenerPagosScout(scoutId);
            
            if (status != null && !status.trim().isEmpty()) {
                payments = payments.stream()
                        .filter(p -> p.getStatus().equals(status))
                        .collect(java.util.stream.Collectors.toList());
            }
            
            response.put("status", "success");
            response.put("data", payments);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al obtener pagos: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/{id}/mark-paid")
    public ResponseEntity<Map<String, Object>> markPaymentAsPaid(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            ScoutPayment pago = paymentService.marcarPagoComoPagado(id);
            
            response.put("status", "success");
            response.put("data", pago);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al marcar pago como pagado: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/instances/pending")
    public ResponseEntity<Map<String, Object>> getPendingInstances(
            @RequestParam(required = false) String scoutId,
            @RequestParam(required = false) String fechaDesde,
            @RequestParam(required = false) String fechaHasta) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (scoutId == null || scoutId.trim().isEmpty()) {
                response.put("status", "error");
                response.put("message", "scoutId es requerido");
                return ResponseEntity.badRequest().body(response);
            }
            
            LocalDate desde = fechaDesde != null ? LocalDate.parse(fechaDesde) : null;
            LocalDate hasta = fechaHasta != null ? LocalDate.parse(fechaHasta) : null;
            
            List<ScoutPaymentInstanceDTO> instancias = instanceService.obtenerInstanciasPendientes(scoutId, desde, hasta);
            
            response.put("status", "success");
            response.put("data", instancias);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al obtener instancias pendientes: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/instances/calculate")
    public ResponseEntity<Map<String, Object>> calculateInstances(
            @RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String scoutId = (String) request.get("scoutId");
            String fechaDesdeStr = (String) request.get("fechaDesde");
            String fechaHastaStr = (String) request.get("fechaHasta");
            
            if (scoutId == null || scoutId.trim().isEmpty()) {
                response.put("status", "error");
                response.put("message", "scoutId es requerido");
                return ResponseEntity.badRequest().body(response);
            }
            
            LocalDate fechaDesde = fechaDesdeStr != null ? LocalDate.parse(fechaDesdeStr) : null;
            LocalDate fechaHasta = fechaHastaStr != null ? LocalDate.parse(fechaHastaStr) : null;
            
            List<ScoutPaymentInstanceDTO> instancias = instanceService.calcularInstanciasPendientes(scoutId, fechaDesde, fechaHasta);
            
            response.put("status", "success");
            response.put("data", instancias);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al calcular instancias: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/instances/pay")
    public ResponseEntity<Map<String, Object>> payInstances(
            @RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String scoutId = (String) request.get("scoutId");
            @SuppressWarnings("unchecked")
            List<Integer> instanceIdsInt = (List<Integer>) request.get("instanceIds");
            
            if (scoutId == null || scoutId.trim().isEmpty()) {
                response.put("status", "error");
                response.put("message", "scoutId es requerido");
                return ResponseEntity.badRequest().body(response);
            }
            
            if (instanceIdsInt == null || instanceIdsInt.isEmpty()) {
                response.put("status", "error");
                response.put("message", "instanceIds es requerido y no puede estar vacío");
                return ResponseEntity.badRequest().body(response);
            }
            
            List<Long> instanceIds = instanceIdsInt.stream()
                    .map(Integer::longValue)
                    .collect(java.util.stream.Collectors.toList());
            
            ScoutPayment pago = paymentService.generarPagoDesdeInstancias(scoutId, instanceIds);
            
            response.put("status", "success");
            response.put("data", pago);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al pagar instancias: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/instances/pay-all")
    public ResponseEntity<Map<String, Object>> payAllInstances(
            @RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String scoutId = (String) request.get("scoutId");
            
            if (scoutId == null || scoutId.trim().isEmpty()) {
                response.put("status", "error");
                response.put("message", "scoutId es requerido");
                return ResponseEntity.badRequest().body(response);
            }
            
            ScoutPayment pago = paymentService.generarPagoTodasPendientes(scoutId);
            
            response.put("status", "success");
            response.put("data", pago);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al pagar todas las instancias: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/instances/weekly-view")
    public ResponseEntity<Map<String, Object>> getWeeklyView(
            @RequestParam String weekISO,
            @RequestParam(required = false) String scoutId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<ScoutWeeklyPaymentViewDTO> vista = instanceService.obtenerVistaSemanal(weekISO);
            
            if (scoutId != null && !scoutId.trim().isEmpty()) {
                vista = vista.stream()
                        .filter(v -> v.getScoutId().equals(scoutId))
                        .collect(java.util.stream.Collectors.toList());
            }
            
            response.put("status", "success");
            response.put("data", vista);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al obtener vista semanal: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/instances/daily-view")
    public ResponseEntity<Map<String, Object>> getDailyView(
            @RequestParam String fecha,
            @RequestParam(required = false) String scoutId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            LocalDate fechaLocal = LocalDate.parse(fecha);
            ScoutPaymentInstanceService.VistaDiariaResultado resultado = instanceService.obtenerVistaDiariaConFecha(fechaLocal);
            List<ScoutWeeklyPaymentViewDTO> vista = resultado.getVista();
            
            if (scoutId != null && !scoutId.trim().isEmpty()) {
                vista = vista.stream()
                        .filter(v -> v.getScoutId().equals(scoutId))
                        .collect(java.util.stream.Collectors.toList());
            }
            
            response.put("status", "success");
            response.put("data", vista);
            response.put("fechaSolicitada", fecha);
            response.put("fechaUsada", resultado.getFechaUsada().toString());
            response.put("hizoFallback", resultado.isHizoFallback());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al obtener vista diaria: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/instances/historical-view")
    public ResponseEntity<Map<String, Object>> getHistoricalView(
            @RequestParam(required = false, defaultValue = "3") Integer meses,
            @RequestParam(required = false, defaultValue = "0") Integer offset,
            @RequestParam(required = false, defaultValue = "50") Integer limit,
            @RequestParam(required = false) String scoutId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            Map<String, Object> resultado = instanceService.obtenerVistaHistorica(meses, offset, limit);
            
            @SuppressWarnings("unchecked")
            List<ScoutWeeklyPaymentViewDTO> vista = (List<ScoutWeeklyPaymentViewDTO>) resultado.get("data");
            
            if (scoutId != null && !scoutId.trim().isEmpty()) {
                vista = vista.stream()
                        .filter(v -> v.getScoutId().equals(scoutId))
                        .collect(java.util.stream.Collectors.toList());
            }
            
            response.put("status", "success");
            response.put("data", vista);
            response.put("total", resultado.get("total"));
            response.put("hasMore", resultado.get("hasMore"));
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al obtener vista histórica: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}

