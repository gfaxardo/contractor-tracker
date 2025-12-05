package com.yego.contractortracker.controller;

import com.yego.contractortracker.dto.ScoutProfileDTO;
import com.yego.contractortracker.dto.ScoutProfileUpdateDTO;
import com.yego.contractortracker.entity.Scout;
import com.yego.contractortracker.service.ScoutService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scouts")
public class ScoutController {
    
    @Autowired
    private ScoutService scoutService;
    
    @GetMapping
    public ResponseEntity<Map<String, Object>> getScouts() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Scout> scouts = scoutService.obtenerScouts();
            response.put("status", "success");
            response.put("data", scouts);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al obtener scouts: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getScoutById(@PathVariable String id) {
        Map<String, Object> response = new HashMap<>();
        try {
            return scoutService.obtenerScoutPorId(id)
                    .map(scout -> {
                        response.put("status", "success");
                        response.put("data", scout);
                        return ResponseEntity.ok(response);
                    })
                    .orElseGet(() -> {
                        response.put("status", "error");
                        response.put("message", "Scout no encontrado: " + id);
                        return ResponseEntity.status(404).body(response);
                    });
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al obtener scout: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping
    public ResponseEntity<Map<String, Object>> createScout(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String nombre = request.get("nombre");
            
            if (nombre == null || nombre.isEmpty()) {
                response.put("status", "error");
                response.put("message", "nombre es requerido");
                return ResponseEntity.badRequest().body(response);
            }
            
            Scout scout = scoutService.crearOActualizarScout(nombre);
            
            response.put("status", "success");
            response.put("data", scout);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al crear scout: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateScout(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String nombre = (String) request.get("nombre");
            String driverId = (String) request.get("driverId");
            Boolean isActive = request.get("isActive") != null ? 
                    Boolean.parseBoolean(request.get("isActive").toString()) : null;
            
            Scout scout = scoutService.actualizarScout(id, nombre, driverId, isActive);
            
            response.put("status", "success");
            response.put("data", scout);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al actualizar scout: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/{id}/drivers")
    public ResponseEntity<Map<String, Object>> getDriversByScout(@PathVariable String id) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<Map<String, Object>> drivers = scoutService.obtenerDriversPorScout(id);
            
            response.put("status", "success");
            response.put("data", drivers);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al obtener drivers: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @GetMapping("/{id}/profile")
    public ResponseEntity<Map<String, Object>> getScoutProfile(@PathVariable String id) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            ScoutProfileDTO profile = scoutService.obtenerPerfilCompleto(id);
            response.put("status", "success");
            response.put("data", profile);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(404).body(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al obtener perfil del scout: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PutMapping("/{id}/profile")
    public ResponseEntity<Map<String, Object>> updateScoutProfile(
            @PathVariable String id,
            @RequestBody ScoutProfileUpdateDTO updateDTO) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            ScoutProfileDTO updatedProfile = scoutService.actualizarPerfil(id, updateDTO);
            response.put("status", "success");
            response.put("data", updatedProfile);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(404).body(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al actualizar perfil del scout: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}

