package com.yego.contractortracker.controller;

import com.yego.contractortracker.dto.YangoPaymentConfigDTO;
import com.yego.contractortracker.entity.YangoPaymentConfig;
import com.yego.contractortracker.service.YangoPaymentConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/yango-payment-config")
public class YangoPaymentConfigController {
    
    @Autowired
    private YangoPaymentConfigService configService;
    
    @GetMapping
    public ResponseEntity<Map<String, Object>> getPaymentConfig() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<YangoPaymentConfig> configs = configService.obtenerConfiguracion();
            List<YangoPaymentConfigDTO> dtos = configs.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
            
            response.put("status", "success");
            response.put("data", dtos);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al obtener configuración: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/period/{periodDays}")
    public ResponseEntity<Map<String, Object>> getPaymentConfigByPeriod(@PathVariable Integer periodDays) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<YangoPaymentConfig> configs = configService.obtenerConfiguracionPorPeriodo(periodDays);
            List<YangoPaymentConfigDTO> dtos = configs.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
            
            response.put("status", "success");
            response.put("data", dtos);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al obtener configuración: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updatePaymentConfig(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            BigDecimal amountYango = null;
            if (request.containsKey("amountYango")) {
                Object amountObj = request.get("amountYango");
                if (amountObj instanceof Number) {
                    amountYango = BigDecimal.valueOf(((Number) amountObj).doubleValue());
                } else if (amountObj instanceof String) {
                    amountYango = new BigDecimal((String) amountObj);
                }
            }
            
            Integer periodDays = null;
            if (request.containsKey("periodDays")) {
                Object periodObj = request.get("periodDays");
                if (periodObj instanceof Number) {
                    periodDays = ((Number) periodObj).intValue();
                } else if (periodObj instanceof String) {
                    periodDays = Integer.parseInt((String) periodObj);
                }
            }
            
            Boolean isActive = null;
            if (request.containsKey("isActive")) {
                Object activeObj = request.get("isActive");
                if (activeObj instanceof Boolean) {
                    isActive = (Boolean) activeObj;
                } else if (activeObj instanceof String) {
                    isActive = Boolean.parseBoolean((String) activeObj);
                }
            }
            
            YangoPaymentConfig updated = configService.actualizarConfiguracion(id, amountYango, periodDays, isActive);
            
            response.put("status", "success");
            response.put("message", "Configuración actualizada exitosamente");
            response.put("data", convertToDTO(updated));
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al actualizar configuración: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping
    public ResponseEntity<Map<String, Object>> createPaymentConfig(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Validar campos requeridos
            if (!request.containsKey("milestoneType") || !request.containsKey("amountYango") || !request.containsKey("periodDays")) {
                response.put("status", "error");
                response.put("message", "milestoneType, amountYango y periodDays son requeridos");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Por ahora, la creación se hace manualmente en la base de datos
            // o a través de la inicialización. Este endpoint puede implementarse después si es necesario.
            response.put("status", "error");
            response.put("message", "Creación de configuración no implementada. Use la inicialización de base de datos.");
            return ResponseEntity.badRequest().body(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al crear configuración: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    private YangoPaymentConfigDTO convertToDTO(YangoPaymentConfig config) {
        YangoPaymentConfigDTO dto = new YangoPaymentConfigDTO();
        dto.setId(config.getId());
        dto.setMilestoneType(config.getMilestoneType());
        dto.setAmountYango(config.getAmountYango());
        dto.setPeriodDays(config.getPeriodDays());
        dto.setIsActive(config.getIsActive());
        dto.setCreatedAt(config.getCreatedAt());
        dto.setLastUpdated(config.getLastUpdated());
        return dto;
    }
}

