package com.yego.contractortracker.service;

import com.yego.contractortracker.entity.Scout;
import com.yego.contractortracker.repository.ScoutRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScoutService {
    
    private static final Logger logger = LoggerFactory.getLogger(ScoutService.class);
    
    @Autowired
    private ScoutRepository scoutRepository;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    public List<Scout> obtenerScouts() {
        return scoutRepository.findAll();
    }
    
    public Optional<Scout> obtenerScoutPorId(String scoutId) {
        return scoutRepository.findById(scoutId);
    }
    
    public Optional<Scout> obtenerScoutPorNombre(String nombre) {
        String normalizedName = normalizarNombre(nombre);
        return scoutRepository.findByScoutName(normalizedName);
    }
    
    @Transactional
    public Scout crearOActualizarScout(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del scout es requerido");
        }
        
        String normalizedName = normalizarNombre(nombre);
        
        if (normalizedName == null || normalizedName.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre del scout no puede estar vacío después de normalizar");
        }
        
        Optional<Scout> existing = scoutRepository.findByScoutName(normalizedName);
        if (existing.isPresent()) {
            return existing.get();
        }
        
        Scout scout = new Scout();
        scout.setScoutId(UUID.randomUUID().toString());
        scout.setScoutName(normalizedName);
        scout.setIsActive(true);
        scout.setCreatedAt(LocalDateTime.now());
        scout.setLastUpdated(LocalDateTime.now());
        
        return scoutRepository.save(scout);
    }
    
    @Transactional
    public Scout actualizarScout(String scoutId, String nombre, String driverId, Boolean isActive) {
        Scout scout = scoutRepository.findById(scoutId)
                .orElseThrow(() -> new RuntimeException("Scout no encontrado: " + scoutId));
        
        if (nombre != null && !nombre.isEmpty()) {
            scout.setScoutName(normalizarNombre(nombre));
        }
        if (driverId != null) {
            scout.setDriverId(driverId);
        }
        if (isActive != null) {
            scout.setIsActive(isActive);
        }
        scout.setLastUpdated(LocalDateTime.now());
        
        return scoutRepository.save(scout);
    }
    
    public List<Map<String, Object>> obtenerDriversPorScout(String scoutId) {
        if (scoutId == null || scoutId.trim().isEmpty()) {
            throw new IllegalArgumentException("scoutId es requerido");
        }
        
        String sql = "SELECT DISTINCT d.driver_id, d.full_name, d.phone, d.hire_date, d.license_number " +
                     "FROM drivers d " +
                     "INNER JOIN yango_transactions yt ON yt.driver_id = d.driver_id " +
                     "WHERE yt.scout_id = ? " +
                     "ORDER BY d.hire_date DESC";
        
        return jdbcTemplate.queryForList(sql, scoutId);
    }
    
    private String normalizarNombre(String name) {
        if (name == null) {
            return null;
        }
        return name.toLowerCase()
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u").replace("ñ", "n")
                .replaceAll("\\s+", " ")
                .trim();
    }
}

