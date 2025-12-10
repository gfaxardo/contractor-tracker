package com.yego.contractortracker.service;

import com.yego.contractortracker.dto.ScoutProfileDTO;
import com.yego.contractortracker.dto.ScoutProfileUpdateDTO;
import com.yego.contractortracker.entity.Scout;
import com.yego.contractortracker.repository.ScoutRepository;
import com.yego.contractortracker.repository.ScoutRegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    
    @Autowired
    private ScoutRegistrationRepository scoutRegistrationRepository;
    
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
    
    public ScoutProfileDTO obtenerPerfilCompleto(String scoutId) {
        Scout scout = scoutRepository.findById(scoutId)
                .orElseThrow(() -> new RuntimeException("Scout no encontrado: " + scoutId));
        
        ScoutProfileDTO dto = convertirScoutAPerfilDTO(scout);
        
        // Calcular métricas
        calcularMetricas(dto, scoutId);
        
        return dto;
    }
    
    @Transactional
    public ScoutProfileDTO actualizarPerfil(String scoutId, ScoutProfileUpdateDTO updateDTO) {
        Scout scout = scoutRepository.findById(scoutId)
                .orElseThrow(() -> new RuntimeException("Scout no encontrado: " + scoutId));
        
        // Actualizar campos editables
        if (updateDTO.getEmail() != null) {
            scout.setEmail(updateDTO.getEmail());
        }
        if (updateDTO.getPhone() != null) {
            scout.setPhone(updateDTO.getPhone());
        }
        if (updateDTO.getAddress() != null) {
            scout.setAddress(updateDTO.getAddress());
        }
        if (updateDTO.getNotes() != null) {
            scout.setNotes(updateDTO.getNotes());
        }
        if (updateDTO.getStartDate() != null) {
            scout.setStartDate(updateDTO.getStartDate());
        }
        if (updateDTO.getStatus() != null) {
            scout.setStatus(updateDTO.getStatus());
        }
        if (updateDTO.getContractType() != null) {
            scout.setContractType(updateDTO.getContractType());
        }
        if (updateDTO.getWorkType() != null) {
            scout.setWorkType(updateDTO.getWorkType());
        }
        if (updateDTO.getPaymentMethod() != null) {
            scout.setPaymentMethod(updateDTO.getPaymentMethod());
        }
        if (updateDTO.getBankAccount() != null) {
            scout.setBankAccount(updateDTO.getBankAccount());
        }
        if (updateDTO.getCommissionRate() != null) {
            scout.setCommissionRate(updateDTO.getCommissionRate());
        }
        if (updateDTO.getIsActive() != null) {
            scout.setIsActive(updateDTO.getIsActive());
        }
        
        scout.setLastUpdated(LocalDateTime.now());
        scout = scoutRepository.save(scout);
        
        ScoutProfileDTO dto = convertirScoutAPerfilDTO(scout);
        calcularMetricas(dto, scoutId);
        
        return dto;
    }
    
    private ScoutProfileDTO convertirScoutAPerfilDTO(Scout scout) {
        ScoutProfileDTO dto = new ScoutProfileDTO();
        dto.setScoutId(scout.getScoutId());
        dto.setScoutName(scout.getScoutName());
        dto.setDriverId(scout.getDriverId());
        dto.setIsActive(scout.getIsActive());
        dto.setEmail(scout.getEmail());
        dto.setPhone(scout.getPhone());
        dto.setAddress(scout.getAddress());
        dto.setNotes(scout.getNotes());
        dto.setStartDate(scout.getStartDate());
        dto.setStatus(scout.getStatus());
        dto.setContractType(scout.getContractType());
        dto.setWorkType(scout.getWorkType());
        dto.setPaymentMethod(scout.getPaymentMethod());
        dto.setBankAccount(scout.getBankAccount());
        dto.setCommissionRate(scout.getCommissionRate());
        dto.setCreatedAt(scout.getCreatedAt());
        dto.setLastUpdated(scout.getLastUpdated());
        return dto;
    }
    
    private void calcularMetricas(ScoutProfileDTO dto, String scoutId) {
        // Total de registros procesados
        Long totalRegistrations = scoutRegistrationRepository.findByScoutId(scoutId).stream()
                .count();
        dto.setTotalRegistrations(totalRegistrations);
        
        // Registros matcheados
        Long matchedRegistrations = scoutRegistrationRepository.findByScoutId(scoutId).stream()
                .filter(r -> r.getIsMatched() != null && r.getIsMatched())
                .count();
        dto.setMatchedRegistrations(matchedRegistrations);
        
        // Total de drivers únicos afiliados
        String sqlDriversUnicos = "SELECT COUNT(DISTINCT driver_id) as total " +
                "FROM scout_registrations " +
                "WHERE scout_id = ? AND driver_id IS NOT NULL";
        Long totalDriversAffiliated = jdbcTemplate.queryForObject(
                sqlDriversUnicos, 
                Long.class, 
                scoutId
        );
        dto.setTotalDriversAffiliated(totalDriversAffiliated != null ? totalDriversAffiliated : 0L);
        
        // Fecha del último registro procesado
        String sqlLastRegistration = "SELECT MAX(registration_date) as last_date " +
                "FROM scout_registrations " +
                "WHERE scout_id = ?";
        try {
            LocalDate lastRegistrationDate = jdbcTemplate.queryForObject(
                    sqlLastRegistration,
                    LocalDate.class,
                    scoutId
            );
            dto.setLastRegistrationDate(lastRegistrationDate);
        } catch (Exception e) {
            logger.debug("No se encontró fecha de último registro para scout: {}", scoutId);
            dto.setLastRegistrationDate(null);
        }
    }
}

