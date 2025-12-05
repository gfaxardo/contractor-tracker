package com.yego.contractortracker.service;

import com.yego.contractortracker.dto.ScoutPaymentInstanceDTO;
import com.yego.contractortracker.entity.*;
import com.yego.contractortracker.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScoutPaymentInstanceService {
    
    private static final Logger logger = LoggerFactory.getLogger(ScoutPaymentInstanceService.class);
    private static final int[] SCOUT_MILESTONE_TYPES = {1, 5, 10};
    private static final int SCOUT_PERIOD_DAYS = 7;
    
    @Autowired
    private ScoutPaymentInstanceRepository instanceRepository;
    
    @Autowired
    private MilestoneInstanceRepository milestoneInstanceRepository;
    
    @Autowired
    private ScoutPaymentConfigRepository configRepository;
    
    @Autowired
    private ScoutRegistrationRepository scoutRegistrationRepository;
    
    @Autowired
    private ScoutRepository scoutRepository;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    public boolean verificarElegibilidadScout(String scoutId, LocalDate registrationDate) {
        try {
            ScoutPaymentConfig config = configRepository.findByIsActiveTrue().stream()
                    .findFirst()
                    .orElse(null);
            
            int minRegistrations = config != null && config.getMinRegistrationsRequired() != null 
                    ? config.getMinRegistrationsRequired() 
                    : 8;
            int minSeconds = config != null && config.getMinConnectionSeconds() != null 
                    ? config.getMinConnectionSeconds() 
                    : 1;
            
            String sql = "SELECT COUNT(DISTINCT sr.driver_id) as registros_con_conexion " +
                        "FROM scout_registrations sr " +
                        "INNER JOIN drivers d ON d.driver_id = sr.driver_id " +
                        "INNER JOIN summary_daily sd ON sd.driver_id = d.driver_id " +
                        "    AND TO_DATE(sd.date_file, 'DD-MM-YYYY') = d.hire_date " +
                        "    AND COALESCE(sd.sum_work_time_seconds, 0) >= ? " +
                        "WHERE sr.scout_id = ? " +
                        "    AND sr.registration_date = ? " +
                        "    AND sr.is_matched = true " +
                        "HAVING COUNT(DISTINCT sr.driver_id) >= ?";
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, minSeconds, scoutId, registrationDate, minRegistrations);
            
            boolean elegible = !results.isEmpty();
            logger.debug("Elegibilidad scout {} para fecha {}: {}", scoutId, registrationDate, elegible);
            return elegible;
        } catch (Exception e) {
            logger.error("Error al verificar elegibilidad del scout {} para fecha {}", scoutId, registrationDate, e);
            return false;
        }
    }
    
    @Transactional
    public List<ScoutPaymentInstance> crearInstanciasDesdeMilestones(String scoutId, LocalDate fechaDesde, LocalDate fechaHasta) {
        logger.info("Creando instancias de pago para scout {} desde {} hasta {}", scoutId, fechaDesde, fechaHasta);
        
        List<ScoutPaymentInstance> instanciasCreadas = new ArrayList<>();
        Set<Integer> milestoneTypesSet = Arrays.stream(SCOUT_MILESTONE_TYPES).boxed().collect(Collectors.toSet());
        Map<Integer, ScoutPaymentConfig> configs = configRepository.findByIsActiveTrue().stream()
                .filter(c -> c.getMilestoneType() != null && milestoneTypesSet.contains(c.getMilestoneType()))
                .collect(Collectors.toMap(ScoutPaymentConfig::getMilestoneType, c -> c));
        
        if (configs.isEmpty()) {
            logger.warn("No hay configuración activa para milestones de scouts");
            return instanciasCreadas;
        }
        
        List<ScoutRegistration> registros = scoutRegistrationRepository.findByScoutIdAndRegistrationDateBetween(
                scoutId, fechaDesde, fechaHasta);
        
        Set<String> driverIds = registros.stream()
                .filter(ScoutRegistration::getIsMatched)
                .map(ScoutRegistration::getDriverId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        if (driverIds.isEmpty()) {
            logger.info("No se encontraron drivers registrados por el scout {} en el período especificado", scoutId);
            return instanciasCreadas;
        }
        
        Map<String, LocalDate> driverHireDates = obtenerHireDates(driverIds);
        Map<String, LocalDate> driverRegistrationDates = registros.stream()
                .filter(ScoutRegistration::getIsMatched)
                .filter(r -> r.getDriverId() != null)
                .collect(Collectors.toMap(
                        ScoutRegistration::getDriverId,
                        ScoutRegistration::getRegistrationDate,
                        (date1, date2) -> date1.isBefore(date2) ? date1 : date2
                ));
        
        for (Integer milestoneType : SCOUT_MILESTONE_TYPES) {
            ScoutPaymentConfig config = configs.get(milestoneType);
            if (config == null || !config.getIsActive()) {
                continue;
            }
            
            List<MilestoneInstance> milestones = milestoneInstanceRepository.findByDriverIdInAndPeriodDays(
                    driverIds, SCOUT_PERIOD_DAYS).stream()
                    .filter(m -> m.getMilestoneType() != null && m.getMilestoneType().equals(milestoneType))
                    .collect(Collectors.toList());
            
            for (MilestoneInstance milestone : milestones) {
                String driverId = milestone.getDriverId();
                LocalDate hireDate = driverHireDates.get(driverId);
                LocalDate registrationDate = driverRegistrationDates.get(driverId);
                
                if (hireDate == null || registrationDate == null) {
                    continue;
                }
                
                if (instanceRepository.findByScoutIdAndDriverIdAndMilestoneType(scoutId, driverId, milestoneType).isPresent()) {
                    continue;
                }
                
                boolean elegible = verificarElegibilidadScout(scoutId, registrationDate);
                String razon = elegible ? "Elegible" : "Scout no cumple requisito de 8 registros con conexión el día de registro";
                
                ScoutPaymentInstance instance = new ScoutPaymentInstance();
                instance.setScoutId(scoutId);
                instance.setDriverId(driverId);
                instance.setMilestoneType(milestoneType);
                instance.setMilestoneInstanceId(milestone.getId());
                instance.setAmount(config.getAmountScout());
                instance.setRegistrationDate(registrationDate);
                instance.setMilestoneFulfillmentDate(milestone.getFulfillmentDate());
                instance.setEligibilityVerified(true);
                instance.setEligibilityReason(razon);
                instance.setStatus(elegible ? "pending" : "cancelled");
                instance.setCreatedAt(LocalDateTime.now());
                instance.setLastUpdated(LocalDateTime.now());
                
                instanciasCreadas.add(instanceRepository.save(instance));
                logger.debug("Instancia creada: scout={}, driver={}, milestone={}, elegible={}", 
                        scoutId, driverId, milestoneType, elegible);
            }
        }
        
        logger.info("Creadas {} instancias de pago para scout {}", instanciasCreadas.size(), scoutId);
        return instanciasCreadas;
    }
    
    private Map<String, LocalDate> obtenerHireDates(Set<String> driverIds) {
        if (driverIds.isEmpty()) {
            return new HashMap<>();
        }
        
        String placeholders = driverIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT driver_id, hire_date FROM drivers WHERE driver_id IN (" + placeholders + ")";
        
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, driverIds.toArray());
        Map<String, LocalDate> hireDates = new HashMap<>();
        
        for (Map<String, Object> row : rows) {
            String driverId = (String) row.get("driver_id");
            Object hireDateObj = row.get("hire_date");
            LocalDate hireDate = null;
            
            if (hireDateObj instanceof java.sql.Date) {
                hireDate = ((java.sql.Date) hireDateObj).toLocalDate();
            } else if (hireDateObj instanceof java.sql.Timestamp) {
                hireDate = ((java.sql.Timestamp) hireDateObj).toLocalDateTime().toLocalDate();
            } else if (hireDateObj instanceof LocalDate) {
                hireDate = (LocalDate) hireDateObj;
            }
            
            if (hireDate != null) {
                hireDates.put(driverId, hireDate);
            }
        }
        
        return hireDates;
    }
    
    public List<ScoutPaymentInstanceDTO> obtenerInstanciasPendientes(String scoutId, LocalDate fechaDesde, LocalDate fechaHasta) {
        List<ScoutPaymentInstance> instancias;
        
        if (fechaDesde != null && fechaHasta != null) {
            instancias = instanceRepository.findByScoutIdAndStatusAndRegistrationDateBetween(
                    scoutId, "pending", fechaDesde, fechaHasta);
        } else {
            instancias = instanceRepository.findByScoutIdAndStatusOrderByRegistrationDateDesc(scoutId, "pending");
        }
        
        return mapearADTOs(instancias);
    }
    
    @Transactional
    public ScoutPayment marcarInstanciasComoPagadas(List<Long> instanceIds, Long paymentId) {
        List<ScoutPaymentInstance> instancias = instanceRepository.findAllById(instanceIds);
        
        for (ScoutPaymentInstance instance : instancias) {
            if ("pending".equals(instance.getStatus())) {
                instance.setStatus("paid");
                instance.setPaymentId(paymentId);
                instance.setLastUpdated(LocalDateTime.now());
                instanceRepository.save(instance);
            }
        }
        
        return null;
    }
    
    private List<ScoutPaymentInstanceDTO> mapearADTOs(List<ScoutPaymentInstance> instancias) {
        Map<String, String> scoutNames = new HashMap<>();
        Map<String, String> driverNames = new HashMap<>();
        
        Set<String> scoutIds = instancias.stream().map(ScoutPaymentInstance::getScoutId).collect(Collectors.toSet());
        Set<String> driverIds = instancias.stream().map(ScoutPaymentInstance::getDriverId).collect(Collectors.toSet());
        
        if (!scoutIds.isEmpty()) {
            List<Scout> scouts = scoutRepository.findAllById(scoutIds);
            scoutNames = scouts.stream().collect(Collectors.toMap(Scout::getScoutId, Scout::getScoutName));
        }
        
        if (!driverIds.isEmpty()) {
            String placeholders = driverIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String sql = "SELECT driver_id, full_name FROM drivers WHERE driver_id IN (" + placeholders + ")";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, driverIds.toArray());
            for (Map<String, Object> row : rows) {
                driverNames.put((String) row.get("driver_id"), (String) row.get("full_name"));
            }
        }
        
        final Map<String, String> finalScoutNames = scoutNames;
        final Map<String, String> finalDriverNames = driverNames;
        
        return instancias.stream().map(instance -> {
            ScoutPaymentInstanceDTO dto = new ScoutPaymentInstanceDTO();
            dto.setId(instance.getId());
            dto.setScoutId(instance.getScoutId());
            dto.setScoutName(finalScoutNames.getOrDefault(instance.getScoutId(), ""));
            dto.setDriverId(instance.getDriverId());
            dto.setDriverName(finalDriverNames.getOrDefault(instance.getDriverId(), ""));
            dto.setMilestoneType(instance.getMilestoneType());
            dto.setMilestoneInstanceId(instance.getMilestoneInstanceId());
            dto.setAmount(instance.getAmount());
            dto.setRegistrationDate(instance.getRegistrationDate());
            dto.setMilestoneFulfillmentDate(instance.getMilestoneFulfillmentDate());
            dto.setEligibilityVerified(instance.getEligibilityVerified());
            dto.setEligibilityReason(instance.getEligibilityReason());
            dto.setStatus(instance.getStatus());
            dto.setPaymentId(instance.getPaymentId());
            dto.setCreatedAt(instance.getCreatedAt());
            dto.setLastUpdated(instance.getLastUpdated());
            return dto;
        }).collect(Collectors.toList());
    }
    
    public List<ScoutPaymentInstanceDTO> calcularInstanciasPendientes(String scoutId, LocalDate fechaDesde, LocalDate fechaHasta) {
        if (fechaDesde == null) {
            fechaDesde = LocalDate.now().minusMonths(3);
        }
        if (fechaHasta == null) {
            fechaHasta = LocalDate.now();
        }
        
        crearInstanciasDesdeMilestones(scoutId, fechaDesde, fechaHasta);
        return obtenerInstanciasPendientes(scoutId, fechaDesde, fechaHasta);
    }
}

