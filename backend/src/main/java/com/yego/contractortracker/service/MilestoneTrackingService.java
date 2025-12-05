package com.yego.contractortracker.service;

import com.yego.contractortracker.dto.MilestoneInstanceDTO;
import com.yego.contractortracker.dto.MilestoneTripDetailDTO;
import com.yego.contractortracker.entity.MilestoneInstance;
import com.yego.contractortracker.repository.MilestoneInstanceRepository;
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
public class MilestoneTrackingService {
    
    private static final Logger logger = LoggerFactory.getLogger(MilestoneTrackingService.class);
    private static final String DEFAULT_PARK_ID = "08e20910d81d42658d4334d3f6d10ac0";
    private static final int[] MILESTONE_TYPES = {1, 5, 25};
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private MilestoneInstanceRepository milestoneInstanceRepository;
    
    @Autowired
    private MilestoneProgressService progressService;
    
    @Autowired
    private com.yego.contractortracker.repository.MilestoneQueryRepository milestoneQueryRepository;
    
    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;
    
    private static final int BATCH_SIZE = 10;
    
    public String calcularInstanciasAsync(String parkId, int periodDays) {
        return calcularInstanciasAsync(parkId, periodDays, null);
    }
    
    public String calcularInstanciasAsync(String parkId, int periodDays, Integer milestoneType) {
        return calcularInstanciasAsync(parkId, periodDays, milestoneType, null, null);
    }
    
    public String calcularInstanciasAsync(String parkId, int periodDays, Integer milestoneType, LocalDate hireDateFrom, LocalDate hireDateTo) {
        try {
            parkId = parkId != null && !parkId.isEmpty() ? parkId : DEFAULT_PARK_ID;
            String jobId = "milestone-" + periodDays + "d-";
            if (milestoneType != null) {
                jobId += milestoneType + "-";
            } else {
                jobId += "all-";
            }
            jobId += System.currentTimeMillis();
            
            logger.info("Iniciando cálculo asíncrono de milestones - jobId: {}, parkId: {}, periodDays: {}, milestoneType: {}, hireDateFrom: {}, hireDateTo: {}", 
                    jobId, parkId, periodDays, milestoneType, hireDateFrom, hireDateTo);
            
            MilestoneTrackingService self = applicationContext.getBean(MilestoneTrackingService.class);
            self.calcularInstanciasAsyncInternal(jobId, parkId, periodDays, milestoneType, hireDateFrom, hireDateTo);
            logger.info("Cálculo asíncrono iniciado - jobId: {} (el procesamiento continuará en segundo plano)", jobId);
            return jobId;
        } catch (Exception e) {
            logger.error("Error al iniciar cálculo asíncrono de milestones", e);
            e.printStackTrace();
            throw new RuntimeException("Error al iniciar cálculo de milestones: " + e.getMessage(), e);
        }
    }
    
    @org.springframework.scheduling.annotation.Async("taskExecutor")
    public void calcularInstanciasAsyncInternal(String jobId, String parkId, int periodDays, Integer milestoneType, LocalDate hireDateFrom, LocalDate hireDateTo) {
        try {
            logger.info("Ejecutando cálculo asíncrono - jobId: {}, periodDays: {}, milestoneType: {}", jobId, periodDays, milestoneType);
            procesarInstanciasAsync(jobId, parkId, periodDays, milestoneType, hireDateFrom, hireDateTo);
            progressService.completeProgress(jobId);
            logger.info("Cálculo asíncrono completado exitosamente - jobId: {}", jobId);
        } catch (Exception e) {
            logger.error("Error en cálculo asíncrono de milestones para {} días - jobId: {}", periodDays, jobId, e);
            e.printStackTrace();
            progressService.failProgress(jobId, e.getMessage());
        }
    }
    
    private void procesarInstanciasAsync(String jobId, String parkId, int periodDays, Integer milestoneTypeFilter, LocalDate hireDateFrom, LocalDate hireDateTo) {
        String periodType = periodDays + " días";
        try {
            logger.info("Iniciando procesamiento de milestones - jobId: {}, periodDays: {}, milestoneTypeFilter: {}", jobId, periodDays, milestoneTypeFilter);
            LocalDateTime calculationDate = LocalDateTime.now();
            
            List<Map<String, Object>> rows = milestoneQueryRepository.obtenerDriversConViajesPorPeriodo(parkId, periodDays, hireDateFrom, hireDateTo);
            logger.info("Drivers encontrados para procesar: {}", rows.size());
            progressService.startProgress(jobId, periodType, rows.size());
            
            Set<String> driverIds = rows.stream()
                    .map(row -> (String) row.get("driver_id"))
                    .collect(Collectors.toSet());
            
            Map<String, MilestoneInstance> existingInstancesMap = cargarInstanciasExistentes(driverIds, periodDays);
            
            int processed = 0;
            int savedCount = 0;
            int milestone1Count = 0;
            int milestone5Count = 0;
            int milestone25Count = 0;
            List<MilestoneInstance> batchToSave = new ArrayList<>();
            
            for (int i = 0; i < rows.size(); i++) {
                Map<String, Object> row = rows.get(i);
                String driverId = (String) row.get("driver_id");
                String driverParkId = (String) row.get("park_id");
                
                Object hireDateObj = row.get("hire_date");
                LocalDate hireDate = null;
                if (hireDateObj instanceof java.sql.Date) {
                    hireDate = ((java.sql.Date) hireDateObj).toLocalDate();
                } else if (hireDateObj instanceof LocalDate) {
                    hireDate = (LocalDate) hireDateObj;
                }
                
                if (hireDate == null) continue;
                
                Integer totalViajes = 0;
                Object totalViajesObj = row.get("total_viajes");
                if (totalViajesObj != null && totalViajesObj instanceof Number) {
                    totalViajes = ((Number) totalViajesObj).intValue();
                }
                
                if (totalViajes < 1) continue;
                
                List<Integer> milestonesAlcanzados = new ArrayList<>();
                for (int milestoneType : MILESTONE_TYPES) {
                    if (totalViajes >= milestoneType) {
                        milestonesAlcanzados.add(milestoneType);
                    }
                }
                
                if (milestonesAlcanzados.isEmpty()) continue;
                
                for (Integer milestoneType : milestonesAlcanzados) {
                    if (milestoneTypeFilter != null && !milestoneType.equals(milestoneTypeFilter)) {
                        continue;
                    }
                    
                    String instanceKey = driverId + "-" + milestoneType + "-" + periodDays;
                    MilestoneInstance instance = existingInstancesMap.get(instanceKey);
                    
                    if (instance == null) {
                        instance = new MilestoneInstance();
                        instance.setDriverId(driverId);
                        instance.setParkId(driverParkId);
                        instance.setMilestoneType(milestoneType);
                        instance.setPeriodDays(periodDays);
                    }
                    
                    instance.setFulfillmentDate(calculationDate);
                    instance.setCalculationDate(calculationDate);
                    instance.setTripCount(totalViajes);
                    instance.setTripDetails(null);
                    
                    batchToSave.add(instance);
                    savedCount++;
                    if (milestoneType == 1) milestone1Count++;
                    else if (milestoneType == 5) milestone5Count++;
                    else if (milestoneType == 25) milestone25Count++;
                }
                
                processed++;
                
                if (batchToSave.size() >= BATCH_SIZE) {
                    guardarBatch(batchToSave);
                    batchToSave.clear();
                }
                
                if (processed % BATCH_SIZE == 0) {
                    progressService.updateProgress(jobId, processed, milestone1Count, milestone5Count, milestone25Count);
                }
            }
            
            if (!batchToSave.isEmpty()) {
                guardarBatch(batchToSave);
            }
            
            progressService.updateProgress(jobId, processed, milestone1Count, milestone5Count, milestone25Count);
            logger.info("Completado período {}: {} instancias guardadas. Milestones: 1={}, 5={}, 25={}", 
                    periodType, savedCount, milestone1Count, milestone5Count, milestone25Count);
            
        } catch (Exception e) {
            logger.error("Error al calcular instancias async para período de {} días - jobId: {}", periodDays, jobId, e);
            progressService.failProgress(jobId, e.getMessage());
            throw e;
        }
    }
    
    private Map<String, MilestoneInstance> cargarInstanciasExistentes(Set<String> driverIds, int periodDays) {
        if (driverIds == null || driverIds.isEmpty()) {
            return new HashMap<>();
        }
        
        Map<String, MilestoneInstance> existingMap = new HashMap<>();
        
        try {
            if (driverIds.size() > 1000) {
                List<String> driverList = new ArrayList<>(driverIds);
                for (int i = 0; i < driverList.size(); i += 1000) {
                    int end = Math.min(i + 1000, driverList.size());
                    List<String> batch = driverList.subList(i, end);
                    cargarBatchInstanciasExistentes(batch, periodDays, existingMap);
                }
            } else {
                cargarBatchInstanciasExistentes(new ArrayList<>(driverIds), periodDays, existingMap);
            }
        } catch (Exception e) {
            logger.warn("Error al cargar instancias existentes, continuando sin cache: {}", e.getMessage());
        }
        
        return existingMap;
    }
    
    private void cargarBatchInstanciasExistentes(List<String> driverIds, int periodDays, Map<String, MilestoneInstance> existingMap) {
        if (driverIds.isEmpty()) return;
        
        try {
            String placeholders = driverIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String sql = "SELECT * FROM milestone_instances WHERE driver_id IN (" + placeholders + ") AND period_days = ?";
            
            List<Object> params = new ArrayList<>();
            params.addAll(driverIds);
            params.add(periodDays);
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
            
            for (Map<String, Object> row : rows) {
                MilestoneInstance instance = new MilestoneInstance();
                instance.setId(((Number) row.get("id")).longValue());
                instance.setDriverId((String) row.get("driver_id"));
                instance.setParkId((String) row.get("park_id"));
                instance.setMilestoneType(((Number) row.get("milestone_type")).intValue());
                instance.setPeriodDays(((Number) row.get("period_days")).intValue());
                instance.setTripCount(((Number) row.get("trip_count")).intValue());
                
                Object fulfillmentDateObj = row.get("fulfillment_date");
                if (fulfillmentDateObj instanceof java.sql.Timestamp) {
                    instance.setFulfillmentDate(((java.sql.Timestamp) fulfillmentDateObj).toLocalDateTime());
                }
                
                Object calculationDateObj = row.get("calculation_date");
                if (calculationDateObj instanceof java.sql.Timestamp) {
                    instance.setCalculationDate(((java.sql.Timestamp) calculationDateObj).toLocalDateTime());
                }
                
                String key = instance.getDriverId() + "-" + instance.getMilestoneType() + "-" + instance.getPeriodDays();
                existingMap.put(key, instance);
            }
        } catch (Exception e) {
            logger.warn("Error al cargar batch de instancias existentes: {}", e.getMessage());
        }
    }
    
    @Transactional
    private void guardarBatch(List<MilestoneInstance> instances) {
        if (instances.isEmpty()) return;
        
        try {
            milestoneInstanceRepository.saveAll(instances);
            logger.debug("Batch guardado exitosamente: {} instancias guardadas", instances.size());
            
            // Log detallado de tipos de milestones guardados
            long milestone1Count = instances.stream().filter(i -> i.getMilestoneType() == 1).count();
            long milestone5Count = instances.stream().filter(i -> i.getMilestoneType() == 5).count();
            long milestone25Count = instances.stream().filter(i -> i.getMilestoneType() == 25).count();
            logger.debug("Desglose del batch: milestone1={}, milestone5={}, milestone25={}", 
                    milestone1Count, milestone5Count, milestone25Count);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            logger.debug("Error de integridad en batch, guardando individualmente (puede haber duplicados): {}", e.getMessage());
            guardarInstanciasIndividualmente(instances);
        } catch (Exception e) {
            logger.warn("Error al guardar batch, intentando individualmente: {}", e.getMessage());
            guardarInstanciasIndividualmente(instances);
        }
    }
    
    private void guardarInstanciasIndividualmente(List<MilestoneInstance> instances) {
        int savedCount = 0;
        int updatedCount = 0;
        int errorCount = 0;
        
        for (MilestoneInstance instance : instances) {
            try {
                Optional<MilestoneInstance> existing = milestoneInstanceRepository
                        .findByDriverIdAndMilestoneTypeAndPeriodDays(
                                instance.getDriverId(), 
                                instance.getMilestoneType(), 
                                instance.getPeriodDays());
                
                if (existing.isPresent()) {
                    MilestoneInstance existingInstance = existing.get();
                    existingInstance.setTripCount(instance.getTripCount());
                    existingInstance.setCalculationDate(instance.getCalculationDate());
                    existingInstance.setFulfillmentDate(instance.getFulfillmentDate());
                    milestoneInstanceRepository.save(existingInstance);
                    updatedCount++;
                } else {
                    milestoneInstanceRepository.save(instance);
                    savedCount++;
                }
            } catch (Exception ex) {
                errorCount++;
                logger.debug("Error al guardar instancia individual: driver={}, milestone={}, period={}, error={}", 
                        instance.getDriverId(), instance.getMilestoneType(), instance.getPeriodDays(), ex.getMessage());
            }
        }
        
        logger.info("Guardado individual completado: {} nuevos, {} actualizados, {} errores", 
                savedCount, updatedCount, errorCount);
    }
    
    public List<MilestoneInstanceDTO> obtenerInstanciasDriver(String driverId) {
        List<MilestoneInstance> instances = milestoneInstanceRepository.findByDriverId(driverId);
        return convertirADTOs(instances);
    }
    
    public MilestoneInstanceDTO obtenerDetalleViajesInstancia(String driverId, int milestoneType, int periodDays) {
        Optional<MilestoneInstance> instanceOpt = milestoneInstanceRepository
            .findByDriverIdAndMilestoneTypeAndPeriodDays(driverId, milestoneType, periodDays);
        
        if (instanceOpt.isEmpty()) {
            return null;
        }
        
        MilestoneInstance instance = instanceOpt.get();
        return convertirADTO(instance);
    }
    
    public List<MilestoneInstanceDTO> obtenerInstanciasPorPeriodo(int periodDays, String parkId) {
        return obtenerInstanciasPorPeriodo(periodDays, parkId, null);
    }
    
    public List<MilestoneInstanceDTO> obtenerInstanciasPorPeriodo(int periodDays, String parkId, Integer milestoneType) {
        parkId = parkId != null && !parkId.isEmpty() ? parkId : DEFAULT_PARK_ID;
        List<MilestoneInstance> instances = new ArrayList<>();
        
        if (milestoneType != null) {
            List<MilestoneInstance> typeInstances = milestoneInstanceRepository
                .findByParkIdAndMilestoneTypeAndPeriodDays(parkId, milestoneType, periodDays);
            instances.addAll(typeInstances);
        } else {
            for (int type : MILESTONE_TYPES) {
                List<MilestoneInstance> typeInstances = milestoneInstanceRepository
                    .findByParkIdAndMilestoneTypeAndPeriodDays(parkId, type, periodDays);
                instances.addAll(typeInstances);
            }
        }
        
        return convertirADTOs(instances);
    }
    
    public Map<String, Object> obtenerInstanciasPorPeriodoConTotales(int periodDays, String parkId, Integer milestoneType) {
        return obtenerInstanciasPorPeriodoConTotales(periodDays, parkId, milestoneType, null, null);
    }
    
    public Map<String, Object> obtenerInstanciasPorPeriodoConTotales(int periodDays, String parkId, Integer milestoneType, LocalDate hireDateFrom, LocalDate hireDateTo) {
        final String finalParkId = parkId != null && !parkId.isEmpty() ? parkId : DEFAULT_PARK_ID;
        
        int totalMilestone1 = 0;
        int totalMilestone5 = 0;
        int totalMilestone25 = 0;
        List<MilestoneInstanceDTO> milestones = new ArrayList<>();
        
        List<Map<String, Object>> rows = milestoneQueryRepository.obtenerDriversConViajesPorPeriodo(finalParkId, periodDays, hireDateFrom, hireDateTo);
        
        Set<String> driverIdsFiltrados = new HashSet<>();
        for (Map<String, Object> row : rows) {
            String driverId = (String) row.get("driver_id");
            if (driverId != null) {
                driverIdsFiltrados.add(driverId);
            }
            
            Integer totalViajes = 0;
            Object totalViajesObj = row.get("total_viajes");
            if (totalViajesObj != null && totalViajesObj instanceof Number) {
                totalViajes = ((Number) totalViajesObj).intValue();
            }
            
            if (totalViajes >= 1) totalMilestone1++;
            if (totalViajes >= 5) totalMilestone5++;
            if (totalViajes >= 25) totalMilestone25++;
        }
        
        if (!driverIdsFiltrados.isEmpty()) {
            if (milestoneType != null) {
                List<MilestoneInstance> typeInstances = milestoneInstanceRepository
                    .findByDriverIdInAndPeriodDays(driverIdsFiltrados, periodDays);
                typeInstances = typeInstances.stream()
                    .filter(mi -> mi.getMilestoneType().equals(milestoneType) && mi.getParkId().equals(finalParkId))
                    .collect(Collectors.toList());
                milestones.addAll(convertirADTOs(typeInstances));
            } else {
                for (int type : MILESTONE_TYPES) {
                    List<MilestoneInstance> typeInstances = milestoneInstanceRepository
                        .findByDriverIdInAndPeriodDays(driverIdsFiltrados, periodDays);
                    typeInstances = typeInstances.stream()
                        .filter(mi -> mi.getMilestoneType().equals(type) && mi.getParkId().equals(finalParkId))
                        .collect(Collectors.toList());
                    milestones.addAll(convertirADTOs(typeInstances));
                }
            }
        }
        
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("totals", Map.of(
            "milestone1", totalMilestone1,
            "milestone5", totalMilestone5,
            "milestone25", totalMilestone25
        ));
        resultado.put("milestones", milestones);
        resultado.put("periodDays", periodDays);
        
        return resultado;
    }
    
    public int obtenerConteoDriversConViajes(String parkId, int periodDays, LocalDate hireDateFrom, LocalDate hireDateTo) {
        parkId = parkId != null && !parkId.isEmpty() ? parkId : DEFAULT_PARK_ID;
        
        try {
            List<Map<String, Object>> rows = milestoneQueryRepository.obtenerDriversConViajesPorPeriodo(parkId, periodDays, hireDateFrom, hireDateTo);
            return rows.size();
        } catch (Exception e) {
            logger.warn("Error al obtener conteo de drivers con viajes: {}", e.getMessage());
            return 0;
        }
    }
    
    public int obtenerTotalViajesDriver(String driverId, int periodDays) {
        try {
            String sql = "SELECT SUM(COALESCE(sd.count_orders_completed, 0)) as total_viajes " +
                    "FROM drivers d " +
                    "INNER JOIN summary_daily sd ON sd.driver_id = d.driver_id " +
                    "WHERE d.driver_id = ? " +
                    "  AND TO_DATE(sd.date_file, 'DD-MM-YYYY') >= d.hire_date::DATE " +
                    "  AND TO_DATE(sd.date_file, 'DD-MM-YYYY') <= (d.hire_date::DATE + INTERVAL '" + periodDays + " day')";
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, driverId);
            if (!rows.isEmpty()) {
                Object totalViajesObj = rows.get(0).get("total_viajes");
                if (totalViajesObj != null && totalViajesObj instanceof Number) {
                    return ((Number) totalViajesObj).intValue();
                }
            }
            return 0;
        } catch (Exception e) {
            logger.warn("Error al obtener total de viajes para driver {}: {}", driverId, e.getMessage());
            return 0;
        }
    }
    
    public Map<String, List<MilestoneInstanceDTO>> obtenerInstanciasPorDriverIds(List<String> driverIds, int periodDays) {
        if (driverIds == null || driverIds.isEmpty()) {
            return new HashMap<>();
        }
        
        Map<String, List<MilestoneInstanceDTO>> result = new HashMap<>();
        
        try {
            String placeholders = driverIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String sql = "SELECT * FROM milestone_instances WHERE driver_id IN (" + placeholders + ") AND period_days = ?";
            
            List<Object> params = new ArrayList<>();
            params.addAll(driverIds);
            params.add(periodDays);
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
            
            for (Map<String, Object> row : rows) {
                String driverId = (String) row.get("driver_id");
                MilestoneInstance instance = new MilestoneInstance();
                instance.setId(((Number) row.get("id")).longValue());
                instance.setDriverId(driverId);
                instance.setParkId((String) row.get("park_id"));
                instance.setMilestoneType(((Number) row.get("milestone_type")).intValue());
                instance.setPeriodDays(((Number) row.get("period_days")).intValue());
                
                Object fulfillmentDateObj = row.get("fulfillment_date");
                if (fulfillmentDateObj instanceof java.sql.Timestamp) {
                    instance.setFulfillmentDate(((java.sql.Timestamp) fulfillmentDateObj).toLocalDateTime());
                } else if (fulfillmentDateObj instanceof LocalDateTime) {
                    instance.setFulfillmentDate((LocalDateTime) fulfillmentDateObj);
                }
                
                Object calculationDateObj = row.get("calculation_date");
                if (calculationDateObj instanceof java.sql.Timestamp) {
                    instance.setCalculationDate(((java.sql.Timestamp) calculationDateObj).toLocalDateTime());
                } else if (calculationDateObj instanceof LocalDateTime) {
                    instance.setCalculationDate((LocalDateTime) calculationDateObj);
                }
                
                Object lastUpdatedObj = row.get("last_updated");
                if (lastUpdatedObj instanceof java.sql.Timestamp) {
                    instance.setLastUpdated(((java.sql.Timestamp) lastUpdatedObj).toLocalDateTime());
                } else if (lastUpdatedObj instanceof LocalDateTime) {
                    instance.setLastUpdated((LocalDateTime) lastUpdatedObj);
                }
                
                instance.setTripCount(((Number) row.get("trip_count")).intValue());
                
                Object tripDetailsObj = row.get("trip_details");
                if (tripDetailsObj != null) {
                    try {
                        if (tripDetailsObj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> tripDetailsMap = (Map<String, Object>) tripDetailsObj;
                            instance.setTripDetails(tripDetailsMap);
                        }
                    } catch (Exception e) {
                        logger.debug("Error al parsear trip_details: {}", e.getMessage());
                    }
                }
                
                result.computeIfAbsent(driverId, k -> new ArrayList<>()).add(convertirADTO(instance));
            }
        } catch (Exception e) {
            logger.warn("Error al obtener milestones por driver IDs, puede que la tabla no exista aún: {}", e.getMessage());
        }
        
        return result;
    }
    
    private MilestoneInstanceDTO convertirADTO(MilestoneInstance instance) {
        MilestoneInstanceDTO dto = new MilestoneInstanceDTO();
        dto.setId(instance.getId());
        dto.setDriverId(instance.getDriverId());
        dto.setParkId(instance.getParkId());
        dto.setMilestoneType(instance.getMilestoneType());
        dto.setPeriodDays(instance.getPeriodDays());
        dto.setFulfillmentDate(instance.getFulfillmentDate());
        dto.setCalculationDate(instance.getCalculationDate());
        dto.setTripCount(instance.getTripCount());
        
        if (instance.getTripDetails() != null) {
            try {
                Object tripsObj = instance.getTripDetails().get("trips");
                if (tripsObj instanceof List<?>) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> tripsList = (List<Map<String, Object>>) tripsObj;
                    List<MilestoneTripDetailDTO> tripDetails = new ArrayList<>();
                    
                    for (Map<String, Object> tripMap : tripsList) {
                        MilestoneTripDetailDTO tripDetail = new MilestoneTripDetailDTO();
                        if (tripMap.get("date") != null) {
                            tripDetail.setDate(LocalDate.parse(tripMap.get("date").toString()));
                        }
                        if (tripMap.get("tripCount") != null) {
                            tripDetail.setTripCount(((Number) tripMap.get("tripCount")).intValue());
                        }
                        if (tripMap.get("dayFromHireDate") != null) {
                            tripDetail.setDayFromHireDate(((Number) tripMap.get("dayFromHireDate")).intValue());
                        }
                        tripDetails.add(tripDetail);
                    }
                    
                    dto.setTripDetails(tripDetails);
                }
            } catch (Exception e) {
                logger.error("Error al convertir trip_details a DTO", e);
            }
        }
        
        return dto;
    }
    
    private List<MilestoneInstanceDTO> convertirADTOs(List<MilestoneInstance> instances) {
        List<MilestoneInstanceDTO> dtos = new ArrayList<>();
        for (MilestoneInstance instance : instances) {
            dtos.add(convertirADTO(instance));
        }
        return dtos;
    }
    
    @Transactional
    public void limpiarMilestones(String parkId) {
        parkId = parkId != null && !parkId.isEmpty() ? parkId : DEFAULT_PARK_ID;
        logger.info("Limpiando milestones para parkId: {}", parkId);
        
        try {
            String sql = "DELETE FROM milestone_instances WHERE park_id = ?";
            int deleted = jdbcTemplate.update(sql, parkId);
            logger.info("Milestones eliminados: {} para parkId: {}", deleted, parkId);
        } catch (Exception e) {
            logger.error("Error al limpiar milestones para parkId: {}", parkId, e);
            throw new RuntimeException("Error al limpiar milestones: " + e.getMessage(), e);
        }
    }
}


