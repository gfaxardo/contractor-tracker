package com.yego.contractortracker.service;

import com.yego.contractortracker.dto.MilestoneInstanceDTO;
import com.yego.contractortracker.dto.MilestonePaymentViewDTO;
import com.yego.contractortracker.dto.MilestoneTripDetailDTO;
import com.yego.contractortracker.entity.MilestoneInstance;
import com.yego.contractortracker.repository.MilestoneInstanceRepository;
import com.yego.contractortracker.util.WeekISOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    
    @Autowired
    private YangoTransactionRematchService yangoTransactionRematchService;
    
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
            
            // Iniciar re-matching automático de transacciones Yango en paralelo
            String rematchJobId = "yango-rematch-milestones-" + System.currentTimeMillis();
            logger.info("Iniciando re-matching automático de transacciones Yango en paralelo. JobId: {}", rematchJobId);
            yangoTransactionRematchService.rematchAllTransactionsAsync(rematchJobId);
            
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
                    
                    // Calcular fecha real de cumplimiento del milestone
                    LocalDate fechaCumplimiento = calcularFechaCumplimientoMilestone(driverId, hireDate, milestoneType, periodDays);
                    instance.setFulfillmentDate(fechaCumplimiento.atStartOfDay());
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
    
    /**
     * Calcula la fecha real en que se alcanzó el milestone dentro del período.
     * Retorna la fecha del primer día donde el acumulado de viajes >= milestoneType.
     * 
     * @param driverId ID del driver
     * @param hireDate Fecha de contratación
     * @param milestoneType Tipo de milestone (1, 5, 25)
     * @param periodDays Días del período (14)
     * @return Fecha de cumplimiento del milestone, o hireDate + periodDays - 1 si no se alcanza
     */
    private LocalDate calcularFechaCumplimientoMilestone(String driverId, LocalDate hireDate, int milestoneType, int periodDays) {
        try {
            LocalDate fechaFin = hireDate.plusDays(periodDays - 1);
            
            // Consultar viajes por día ordenados por fecha
            String sql = "SELECT TO_DATE(sd.date_file, 'DD-MM-YYYY') as fecha, " +
                    "  COALESCE(sd.count_orders_completed, 0) as viajes " +
                    "FROM summary_daily sd " +
                    "WHERE sd.driver_id = ? " +
                    "  AND TO_DATE(sd.date_file, 'DD-MM-YYYY') >= ? " +
                    "  AND TO_DATE(sd.date_file, 'DD-MM-YYYY') <= ? " +
                    "ORDER BY TO_DATE(sd.date_file, 'DD-MM-YYYY') ASC";
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, driverId, hireDate, fechaFin);
            
            if (rows.isEmpty()) {
                // Si no hay datos, retornar último día del período
                return fechaFin;
            }
            
            int acumulado = 0;
            for (Map<String, Object> row : rows) {
                Object fechaObj = row.get("fecha");
                Object viajesObj = row.get("viajes");
                
                if (fechaObj == null || viajesObj == null) continue;
                
                LocalDate fecha;
                if (fechaObj instanceof java.sql.Date) {
                    fecha = ((java.sql.Date) fechaObj).toLocalDate();
                } else if (fechaObj instanceof LocalDate) {
                    fecha = (LocalDate) fechaObj;
                } else {
                    continue;
                }
                
                int viajes = ((Number) viajesObj).intValue();
                acumulado += viajes;
                
                // Si alcanzamos el milestone, retornar esta fecha
                if (acumulado >= milestoneType) {
                    logger.debug("Milestone {} alcanzado para driver {} en fecha {}", milestoneType, driverId, fecha);
                    return fecha;
                }
            }
            
            // Si no se alcanzó el milestone en el período, retornar último día
            logger.debug("Milestone {} no alcanzado completamente para driver {} en período, retornando último día", milestoneType, driverId);
            return fechaFin;
            
        } catch (Exception e) {
            logger.error("Error al calcular fecha de cumplimiento del milestone {} para driver {}: {}", milestoneType, driverId, e.getMessage(), e);
            // En caso de error, retornar último día del período como fallback
            return hireDate.plusDays(periodDays - 1);
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
    
    public List<MilestonePaymentViewDTO> getMilestonePaymentViewWeekly(String weekISO, String parkId) {
        parkId = parkId != null && !parkId.isEmpty() ? parkId : DEFAULT_PARK_ID;
        
        try {
            LocalDate[] weekRange = WeekISOUtil.getWeekRange(weekISO);
            if (weekRange == null || weekRange.length != 2) {
                logger.warn("Semana ISO inválida: {}", weekISO);
                return new ArrayList<>();
            }
            
            LocalDate weekStart = weekRange[0];
            LocalDate weekEnd = weekRange[1];
            
            return getMilestonePaymentViewByDateRange(weekStart, weekEnd, parkId);
        } catch (Exception e) {
            logger.error("Error al obtener vista de pagos semanal para semana {}: {}", weekISO, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    public List<MilestonePaymentViewDTO> getMilestonePaymentViewDaily(LocalDate fecha, String parkId) {
        parkId = parkId != null && !parkId.isEmpty() ? parkId : DEFAULT_PARK_ID;
        
        try {
            return getMilestonePaymentViewByDateRange(fecha, fecha, parkId);
        } catch (Exception e) {
            logger.error("Error al obtener vista de pagos diaria para fecha {}: {}", fecha, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    public List<MilestonePaymentViewDTO> getMilestonePaymentViewByDateRange(LocalDate fechaDesde, LocalDate fechaHasta, String parkId) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        sql.append("  mi.id as milestone_instance_id, ");
        sql.append("  mi.driver_id, ");
        sql.append("  d.full_name as driver_name, ");
        sql.append("  d.phone as driver_phone, ");
        sql.append("  d.hire_date, ");
        sql.append("  mi.milestone_type, ");
        sql.append("  mi.period_days, ");
        sql.append("  mi.fulfillment_date, ");
        sql.append("  mi.trip_count, ");
        sql.append("  yt.id as yango_transaction_id, ");
        sql.append("  yt.amount_yango, ");
        sql.append("  yt.transaction_date as yango_payment_date, ");
        sql.append("  CASE WHEN yt.id IS NOT NULL THEN true ELSE false END as has_payment, ");
        sql.append("  CASE WHEN lm.driver_id IS NOT NULL THEN true ELSE false END as has_lead_match ");
        sql.append("FROM milestone_instances mi ");
        sql.append("INNER JOIN drivers d ON d.driver_id = mi.driver_id ");
        sql.append("INNER JOIN lead_matches lm ON lm.driver_id = mi.driver_id AND lm.is_discarded = false ");
        sql.append("LEFT JOIN yango_transactions yt ON yt.milestone_instance_id = mi.id ");
        sql.append("WHERE mi.period_days = 14 ");
        sql.append("  AND mi.park_id = ? ");
        sql.append("  AND DATE(d.hire_date) >= ? ");
        sql.append("  AND DATE(d.hire_date) <= ? ");
        sql.append("ORDER BY d.hire_date DESC, mi.driver_id, mi.milestone_type ");
        
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql.toString(), 
                parkId, 
                fechaDesde, 
                fechaHasta
            );
            
            List<MilestonePaymentViewDTO> result = new ArrayList<>();
            LocalDate now = LocalDate.now();
            
            for (Map<String, Object> row : rows) {
                MilestonePaymentViewDTO dto = new MilestonePaymentViewDTO();
                
                // Driver info
                dto.setDriverId((String) row.get("driver_id"));
                dto.setDriverName((String) row.get("driver_name"));
                dto.setDriverPhone((String) row.get("driver_phone"));
                Object hireDateObj = row.get("hire_date");
                if (hireDateObj instanceof java.sql.Date) {
                    dto.setHireDate(((java.sql.Date) hireDateObj).toLocalDate());
                } else if (hireDateObj instanceof LocalDate) {
                    dto.setHireDate((LocalDate) hireDateObj);
                }
                
                // Milestone info
                Object milestoneIdObj = row.get("milestone_instance_id");
                if (milestoneIdObj instanceof Number) {
                    dto.setMilestoneInstanceId(((Number) milestoneIdObj).longValue());
                }
                Object milestoneTypeObj = row.get("milestone_type");
                if (milestoneTypeObj instanceof Number) {
                    dto.setMilestoneType(((Number) milestoneTypeObj).intValue());
                }
                Object periodDaysObj = row.get("period_days");
                if (periodDaysObj instanceof Number) {
                    dto.setPeriodDays(((Number) periodDaysObj).intValue());
                }
                Object fulfillmentDateObj = row.get("fulfillment_date");
                if (fulfillmentDateObj instanceof java.sql.Timestamp) {
                    dto.setFulfillmentDate(((java.sql.Timestamp) fulfillmentDateObj).toLocalDateTime());
                } else if (fulfillmentDateObj instanceof LocalDateTime) {
                    dto.setFulfillmentDate((LocalDateTime) fulfillmentDateObj);
                }
                Object tripCountObj = row.get("trip_count");
                if (tripCountObj instanceof Number) {
                    dto.setTripCount(((Number) tripCountObj).intValue());
                }
                
                // Yango payment info
                Object yangoTransactionIdObj = row.get("yango_transaction_id");
                if (yangoTransactionIdObj instanceof Number) {
                    dto.setYangoTransactionId(((Number) yangoTransactionIdObj).longValue());
                }
                Object amountYangoObj = row.get("amount_yango");
                if (amountYangoObj instanceof BigDecimal) {
                    dto.setAmountYango((BigDecimal) amountYangoObj);
                } else if (amountYangoObj instanceof Number) {
                    dto.setAmountYango(BigDecimal.valueOf(((Number) amountYangoObj).doubleValue()));
                }
                Object yangoPaymentDateObj = row.get("yango_payment_date");
                if (yangoPaymentDateObj instanceof java.sql.Timestamp) {
                    dto.setYangoPaymentDate(((java.sql.Timestamp) yangoPaymentDateObj).toLocalDateTime());
                } else if (yangoPaymentDateObj instanceof LocalDateTime) {
                    dto.setYangoPaymentDate((LocalDateTime) yangoPaymentDateObj);
                }
                Object hasPaymentObj = row.get("has_payment");
                if (hasPaymentObj instanceof Boolean) {
                    dto.setHasPayment((Boolean) hasPaymentObj);
                } else {
                    dto.setHasPayment(false);
                }
                
                // Lead match info
                Object hasLeadMatchObj = row.get("has_lead_match");
                if (hasLeadMatchObj instanceof Boolean) {
                    dto.setHasLeadMatch((Boolean) hasLeadMatchObj);
                } else {
                    // Si hay INNER JOIN con lead_matches, siempre será true
                    dto.setHasLeadMatch(true);
                }
                
                // Payment status
                if (dto.getHasPayment() != null && dto.getHasPayment()) {
                    dto.setPaymentStatus("paid");
                } else {
                    LocalDate fulfillmentDate = dto.getFulfillmentDate() != null ? 
                        dto.getFulfillmentDate().toLocalDate() : null;
                    if (fulfillmentDate != null) {
                        long daysSinceFulfillment = java.time.temporal.ChronoUnit.DAYS.between(fulfillmentDate, now);
                        if (daysSinceFulfillment <= 7) {
                            dto.setPaymentStatus("pending");
                        } else {
                            dto.setPaymentStatus("missing");
                        }
                    } else {
                        dto.setPaymentStatus("missing");
                    }
                }
                
                result.add(dto);
            }
            
            return result;
        } catch (Exception e) {
            logger.error("Error al ejecutar consulta de vista de pagos: {}", e.getMessage(), e);
            throw new RuntimeException("Error al obtener vista de pagos: " + e.getMessage(), e);
        }
    }
    
    public List<MilestonePaymentViewDTO> getMilestonePaymentViewPending(String parkId, Integer milestoneType, LocalDate fechaDesde, LocalDate fechaHasta) {
        // Si no se especifica rango de fechas, usar un rango amplio (últimos 6 meses)
        if (fechaDesde == null) {
            fechaDesde = LocalDate.now().minusMonths(6);
        }
        if (fechaHasta == null) {
            fechaHasta = LocalDate.now();
        }
        
        // Obtener todos los milestones en el rango
        List<MilestonePaymentViewDTO> allMilestones = getMilestonePaymentViewByDateRange(fechaDesde, fechaHasta, parkId);
        
        // Filtrar solo los pendientes (sin pago o con estado missing/pending)
        return allMilestones.stream()
            .filter(dto -> {
                // Filtrar por milestoneType si se especifica
                if (milestoneType != null && !dto.getMilestoneType().equals(milestoneType)) {
                    return false;
                }
                
                // Filtrar por pagos pendientes
                Boolean hasPayment = dto.getHasPayment();
                String paymentStatus = dto.getPaymentStatus();
                
                // Pendiente si no tiene pago o el estado es missing/pending
                return hasPayment == null || !hasPayment || 
                       "missing".equals(paymentStatus) || "pending".equals(paymentStatus);
            })
            .collect(java.util.stream.Collectors.toList());
    }
}


