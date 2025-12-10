package com.yego.contractortracker.service;

import com.yego.contractortracker.dto.ScoutPaymentInstanceDTO;
import com.yego.contractortracker.dto.ScoutWeeklyPaymentViewDTO;
import com.yego.contractortracker.dto.DriverWeeklyInfoDTO;
import com.yego.contractortracker.entity.*;
import com.yego.contractortracker.repository.*;
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
public class ScoutPaymentInstanceService {
    
    private static final Logger logger = LoggerFactory.getLogger(ScoutPaymentInstanceService.class);
    private static final int[] SCOUT_MILESTONE_TYPES = {1, 5, 25};
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
    
    /**
     * Verifica elegibilidad de scout para múltiples fechas de registro en una sola consulta batch
     * @param scoutId ID del scout
     * @param driverRegistrationDates Map con driverId como key y registrationDate como valor
     * @return Map con key "scoutId-registrationDate" y valor boolean indicando elegibilidad
     */
    private Map<String, Boolean> verificarElegibilidadScoutBatch(String scoutId, Map<String, LocalDate> driverRegistrationDates) {
        Map<String, Boolean> elegibilidad = new HashMap<>();
        
        if (driverRegistrationDates == null || driverRegistrationDates.isEmpty()) {
            return elegibilidad;
        }
        
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
            
            // Agrupar por fecha de registro para hacer consulta batch
            Map<LocalDate, Set<String>> driversPorFecha = new HashMap<>();
            for (Map.Entry<String, LocalDate> entry : driverRegistrationDates.entrySet()) {
                LocalDate fecha = entry.getValue();
                if (fecha != null) {
                    driversPorFecha.computeIfAbsent(fecha, k -> new HashSet<>()).add(entry.getKey());
                }
            }
            
            // Inicializar todos como no elegibles
            for (Map.Entry<String, LocalDate> entry : driverRegistrationDates.entrySet()) {
                String key = scoutId + "-" + entry.getValue();
                elegibilidad.put(key, false);
            }
            
            // Verificar elegibilidad por cada fecha única
            for (Map.Entry<LocalDate, Set<String>> fechaEntry : driversPorFecha.entrySet()) {
                LocalDate registrationDate = fechaEntry.getKey();
                
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
                
                List<Map<String, Object>> results = jdbcTemplate.queryForList(
                        sql, minSeconds, scoutId, registrationDate, minRegistrations);
                
                boolean esElegible = !results.isEmpty();
                String key = scoutId + "-" + registrationDate;
                elegibilidad.put(key, esElegible);
            }
            
        } catch (Exception e) {
            logger.error("Error al verificar elegibilidad en batch para scout {}: {}", scoutId, e.getMessage(), e);
            // Ya están inicializados como false
        }
        
        return elegibilidad;
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
    
    public List<ScoutWeeklyPaymentViewDTO> obtenerVistaSemanal(String weekISO) {
        logger.info("Obteniendo vista semanal para semana ISO: {}", weekISO);
        
        LocalDate[] weekRange = WeekISOUtil.getWeekRange(weekISO);
        if (weekRange == null || weekRange.length != 2) {
            logger.error("Error al obtener rango de fechas para semana ISO: {}", weekISO);
            return new ArrayList<>();
        }
        
        LocalDate fechaInicio = weekRange[0];
        LocalDate fechaFin = weekRange[1];
        
        List<ScoutRegistration> registros = scoutRegistrationRepository
                .findByRegistrationDateBetween(fechaInicio, fechaFin)
                .stream()
                .filter(ScoutRegistration::getIsMatched)
                .filter(r -> r.getScoutId() != null && !r.getScoutId().trim().isEmpty())
                .filter(r -> r.getDriverId() != null && !r.getDriverId().trim().isEmpty())
                .collect(Collectors.toList());
        
        if (registros.isEmpty()) {
            logger.info("No se encontraron registros de scouts en el período especificado");
            return new ArrayList<>();
        }
        
        List<ScoutWeeklyPaymentViewDTO> vista = procesarRegistrosParaVista(registros, fechaInicio, fechaFin);
        logger.info("Vista semanal generada con {} scouts", vista.size());
        return vista;
    }
    
    /**
     * Verifica conexiones para múltiples drivers en una sola consulta batch
     * @param driverIds Set de IDs de drivers
     * @param hireDates Map con driverId como key y hireDate como valor
     * @return Map con driverId como key y boolean indicando si tiene conexión
     */
    private Map<String, Boolean> verificarConexiones(Set<String> driverIds, Map<String, LocalDate> hireDates) {
        Map<String, Boolean> conexiones = new HashMap<>();
        
        if (driverIds == null || driverIds.isEmpty()) {
            return conexiones;
        }
        
        // Inicializar todos como false
        for (String driverId : driverIds) {
            conexiones.put(driverId, false);
        }
        
        // Filtrar drivers con hireDate válido
        Map<String, LocalDate> driversConHireDate = hireDates.entrySet().stream()
                .filter(e -> e.getValue() != null && driverIds.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        
        if (driversConHireDate.isEmpty()) {
            return conexiones;
        }
        
        try {
            // Construir consulta batch usando CASE WHEN para cada driver
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT driver_id, ");
            sql.append("  CASE WHEN COUNT(*) > 0 THEN true ELSE false END as tiene_conexion ");
            sql.append("FROM summary_daily sd ");
            sql.append("WHERE (");
            
            List<Object> params = new ArrayList<>();
            boolean first = true;
            for (Map.Entry<String, LocalDate> entry : driversConHireDate.entrySet()) {
                if (!first) {
                    sql.append(" OR ");
                }
                sql.append("(sd.driver_id = ? AND TO_DATE(sd.date_file, 'DD-MM-YYYY') = ?)");
                params.add(entry.getKey());
                params.add(entry.getValue());
                first = false;
            }
            
            sql.append(") ");
            sql.append("  AND COALESCE(sd.sum_work_time_seconds, 0) > 0 ");
            sql.append("GROUP BY driver_id");
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            
            for (Map<String, Object> row : results) {
                String driverId = (String) row.get("driver_id");
                Boolean tieneConexion = (Boolean) row.get("tiene_conexion");
                if (driverId != null && tieneConexion != null) {
                    conexiones.put(driverId, tieneConexion);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error al verificar conexiones en batch: {}", e.getMessage(), e);
            // Ya están inicializados como false, no hay que hacer nada más
        }
        
        return conexiones;
    }
    
    private Map<String, Integer> contarRegistrosPorDia(String scoutId, LocalDate fechaInicio, LocalDate fechaFin) {
        Map<String, Integer> registrosPorDia = new HashMap<>();
        
        try {
            String sql = "SELECT registration_date::text as fecha, COUNT(DISTINCT driver_id) as cantidad " +
                        "FROM scout_registrations " +
                        "WHERE scout_id = ? " +
                        "  AND registration_date BETWEEN ? AND ? " +
                        "  AND is_matched = true " +
                        "GROUP BY registration_date";
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, scoutId, fechaInicio, fechaFin);
            
            for (Map<String, Object> row : results) {
                String fecha = (String) row.get("fecha");
                Integer cantidad = ((Number) row.get("cantidad")).intValue();
                registrosPorDia.put(fecha, cantidad);
            }
        } catch (Exception e) {
            logger.error("Error al contar registros por día para scout {}: {}", scoutId, e.getMessage());
        }
        
        return registrosPorDia;
    }
    
    /**
     * Obtiene todas las instancias de pago para un scout y múltiples drivers en una sola consulta batch
     * @param scoutId ID del scout
     * @param driverIds Set de IDs de drivers
     * @return Map con key "scoutId-driverId-milestoneType" y valor ScoutPaymentInstance
     */
    private Map<String, ScoutPaymentInstance> obtenerTodasInstanciasPago(String scoutId, Set<String> driverIds) {
        if (driverIds == null || driverIds.isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            String placeholders = driverIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String sql = "SELECT * FROM scout_payment_instances " +
                        "WHERE scout_id = ? " +
                        "  AND driver_id IN (" + placeholders + ") " +
                        "  AND milestone_type IN (1, 5, 25)";
            
            List<Object> params = new ArrayList<>();
            params.add(scoutId);
            params.addAll(driverIds);
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
            Map<String, ScoutPaymentInstance> instancesMap = new HashMap<>();
            
            for (Map<String, Object> row : rows) {
                ScoutPaymentInstance instance = new ScoutPaymentInstance();
                instance.setId(((Number) row.get("id")).longValue());
                instance.setScoutId((String) row.get("scout_id"));
                instance.setDriverId((String) row.get("driver_id"));
                instance.setMilestoneType(((Number) row.get("milestone_type")).intValue());
                
                Object milestoneInstanceIdObj = row.get("milestone_instance_id");
                instance.setMilestoneInstanceId(milestoneInstanceIdObj != null ? 
                    ((Number) milestoneInstanceIdObj).longValue() : null);
                
                instance.setAmount((java.math.BigDecimal) row.get("amount"));
                
                java.sql.Date regDate = (java.sql.Date) row.get("registration_date");
                instance.setRegistrationDate(regDate != null ? regDate.toLocalDate() : null);
                
                java.sql.Timestamp fulfillmentDate = (java.sql.Timestamp) row.get("milestone_fulfillment_date");
                instance.setMilestoneFulfillmentDate(fulfillmentDate != null ? 
                    fulfillmentDate.toLocalDateTime() : null);
                
                instance.setEligibilityVerified((Boolean) row.get("eligibility_verified"));
                instance.setEligibilityReason((String) row.get("eligibility_reason"));
                instance.setStatus((String) row.get("status"));
                
                Object paymentIdObj = row.get("payment_id");
                instance.setPaymentId(paymentIdObj != null ? ((Number) paymentIdObj).longValue() : null);
                
                java.sql.Timestamp createdAt = (java.sql.Timestamp) row.get("created_at");
                instance.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
                
                java.sql.Timestamp lastUpdated = (java.sql.Timestamp) row.get("last_updated");
                instance.setLastUpdated(lastUpdated != null ? lastUpdated.toLocalDateTime() : null);
                
                String key = scoutId + "-" + instance.getDriverId() + "-" + instance.getMilestoneType();
                instancesMap.put(key, instance);
            }
            
            return instancesMap;
        } catch (Exception e) {
            logger.error("Error al obtener instancias de pago en batch para scout {}: {}", scoutId, e.getMessage(), e);
            return new HashMap<>();
        }
    }
    
    /**
     * Obtiene los nombres de múltiples drivers en una sola consulta batch
     * @param driverIds Set de IDs de drivers
     * @return Map con driverId como key y full_name como valor
     */
    private Map<String, String> obtenerNombresDrivers(Set<String> driverIds) {
        if (driverIds == null || driverIds.isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            String placeholders = driverIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String sql = "SELECT driver_id, full_name FROM drivers WHERE driver_id IN (" + placeholders + ")";
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, driverIds.toArray());
            Map<String, String> nombres = new HashMap<>();
            
            for (Map<String, Object> row : rows) {
                String driverId = (String) row.get("driver_id");
                String nombre = (String) row.get("full_name");
                nombres.put(driverId, nombre != null ? nombre : driverId);
            }
            
            // Para drivers no encontrados, usar el driverId como nombre
            for (String driverId : driverIds) {
                if (!nombres.containsKey(driverId)) {
                    nombres.put(driverId, driverId);
                }
            }
            
            return nombres;
        } catch (Exception e) {
            logger.error("Error al obtener nombres de drivers en batch: {}", e.getMessage(), e);
            // Retornar map con driverIds como nombres por defecto
            return driverIds.stream().collect(Collectors.toMap(id -> id, id -> id));
        }
    }
    
    /**
     * Obtiene los teléfonos de múltiples drivers en una sola consulta batch
     * @param driverIds Set de IDs de drivers
     * @return Map con driverId como key y phone como valor
     */
    private Map<String, String> obtenerTelefonosDrivers(Set<String> driverIds) {
        if (driverIds == null || driverIds.isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            String placeholders = driverIds.stream().map(id -> "?").collect(Collectors.joining(","));
            String sql = "SELECT driver_id, phone FROM drivers WHERE driver_id IN (" + placeholders + ")";
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, driverIds.toArray());
            Map<String, String> telefonos = new HashMap<>();
            
            for (Map<String, Object> row : rows) {
                String driverId = (String) row.get("driver_id");
                String telefono = (String) row.get("phone");
                if (telefono != null) {
                    telefonos.put(driverId, telefono);
                }
            }
            
            return telefonos;
        } catch (Exception e) {
            logger.error("Error al obtener teléfonos de drivers en batch: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }
    
    private String obtenerNombreDriver(String driverId) {
        try {
            String sql = "SELECT full_name FROM drivers WHERE driver_id = ?";
            String nombre = jdbcTemplate.queryForObject(sql, String.class, driverId);
            return nombre != null ? nombre : driverId;
        } catch (Exception e) {
            logger.debug("Error al obtener nombre del driver {}: {}", driverId, e.getMessage());
            return driverId;
        }
    }
    
    private String obtenerTelefonoDriver(String driverId) {
        try {
            String sql = "SELECT phone FROM drivers WHERE driver_id = ?";
            return jdbcTemplate.queryForObject(sql, String.class, driverId);
        } catch (Exception e) {
            logger.debug("Error al obtener teléfono del driver {}: {}", driverId, e.getMessage());
            return null;
        }
    }
    
    public List<ScoutWeeklyPaymentViewDTO> obtenerVistaDiaria(LocalDate fecha) {
        return obtenerVistaDiariaConFecha(fecha).getVista();
    }
    
    /**
     * Obtiene la vista diaria y retorna también la fecha real usada (puede ser diferente si hizo fallback)
     */
    public VistaDiariaResultado obtenerVistaDiariaConFecha(LocalDate fecha) {
        logger.info("Obteniendo vista diaria para fecha: {}", fecha);
        
        // Intentar primero con la fecha especificada
        List<ScoutRegistration> registros = obtenerRegistrosPorFecha(fecha);
        LocalDate fechaUsada = fecha;
        boolean hizoFallback = false;
        
        // Si no hay registros, buscar en días anteriores (hasta 7 días atrás)
        if (registros.isEmpty()) {
            logger.info("No se encontraron registros para la fecha {}, buscando en días anteriores...", fecha);
            hizoFallback = true;
            for (int i = 1; i <= 7; i++) {
                LocalDate fechaAnterior = fecha.minusDays(i);
                registros = obtenerRegistrosPorFecha(fechaAnterior);
                if (!registros.isEmpty()) {
                    fechaUsada = fechaAnterior;
                    logger.info("Encontrados {} registros en fecha anterior: {}", registros.size(), fechaAnterior);
                    break;
                }
            }
        }
        
        // Si aún no hay registros, buscar en días posteriores (hasta 7 días adelante)
        if (registros.isEmpty()) {
            logger.info("No se encontraron registros en días anteriores, buscando en días posteriores...");
            for (int i = 1; i <= 7; i++) {
                LocalDate fechaPosterior = fecha.plusDays(i);
                registros = obtenerRegistrosPorFecha(fechaPosterior);
                if (!registros.isEmpty()) {
                    fechaUsada = fechaPosterior;
                    logger.info("Encontrados {} registros en fecha posterior: {}", registros.size(), fechaPosterior);
                    break;
                }
            }
        }
        
        if (registros.isEmpty()) {
            logger.info("No se encontraron registros de scouts en un rango de 7 días alrededor de la fecha: {}", fecha);
            return new VistaDiariaResultado(new ArrayList<>(), fecha, false);
        }
        
        logger.info("Usando {} registros de la fecha {} (fecha solicitada: {})", registros.size(), fechaUsada, fecha);
        List<ScoutWeeklyPaymentViewDTO> vista = procesarRegistrosParaVista(registros, fechaUsada, fechaUsada);
        return new VistaDiariaResultado(vista, fechaUsada, hizoFallback && !fechaUsada.equals(fecha));
    }
    
    /**
     * Clase auxiliar para retornar la vista diaria con la fecha real usada
     */
    public static class VistaDiariaResultado {
        private final List<ScoutWeeklyPaymentViewDTO> vista;
        private final LocalDate fechaUsada;
        private final boolean hizoFallback;
        
        public VistaDiariaResultado(List<ScoutWeeklyPaymentViewDTO> vista, LocalDate fechaUsada, boolean hizoFallback) {
            this.vista = vista;
            this.fechaUsada = fechaUsada;
            this.hizoFallback = hizoFallback;
        }
        
        public List<ScoutWeeklyPaymentViewDTO> getVista() {
            return vista;
        }
        
        public LocalDate getFechaUsada() {
            return fechaUsada;
        }
        
        public boolean isHizoFallback() {
            return hizoFallback;
        }
    }
    
    /**
     * Obtiene registros de scout_registrations para una fecha específica
     * @param fecha Fecha para buscar registros
     * @return Lista de registros encontrados
     */
    private List<ScoutRegistration> obtenerRegistrosPorFecha(LocalDate fecha) {
        // Usar CAST para asegurar comparación exacta de fechas
        String sql = "SELECT * FROM scout_registrations " +
                    "WHERE CAST(registration_date AS DATE) = CAST(? AS DATE) " +
                    "  AND is_matched = true " +
                    "  AND scout_id IS NOT NULL " +
                    "  AND scout_id != '' " +
                    "  AND driver_id IS NOT NULL " +
                    "  AND driver_id != '' " +
                    "ORDER BY scout_id, driver_id";
        
        logger.debug("Buscando registros para fecha: {}", fecha);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, java.sql.Date.valueOf(fecha));
        logger.debug("Encontradas {} filas de la base de datos para fecha {}", rows.size(), fecha);
        
        List<ScoutRegistration> registros = new ArrayList<>();
        
        for (Map<String, Object> row : rows) {
            ScoutRegistration registro = new ScoutRegistration();
            registro.setId(((Number) row.get("id")).longValue());
            registro.setScoutId((String) row.get("scout_id"));
            
            java.sql.Date regDate = (java.sql.Date) row.get("registration_date");
            registro.setRegistrationDate(regDate != null ? regDate.toLocalDate() : null);
            
            registro.setDriverLicense((String) row.get("driver_license"));
            registro.setDriverName((String) row.get("driver_name"));
            registro.setDriverPhone((String) row.get("driver_phone"));
            registro.setAcquisitionMedium((String) row.get("acquisition_medium"));
            registro.setDriverId((String) row.get("driver_id"));
            
            Object matchScoreObj = row.get("match_score");
            registro.setMatchScore(matchScoreObj != null ? ((Number) matchScoreObj).doubleValue() : null);
            
            registro.setIsMatched((Boolean) row.get("is_matched"));
            
            java.sql.Timestamp createdAt = (java.sql.Timestamp) row.get("created_at");
            registro.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
            
            java.sql.Timestamp lastUpdated = (java.sql.Timestamp) row.get("last_updated");
            registro.setLastUpdated(lastUpdated != null ? lastUpdated.toLocalDateTime() : null);
            
            registros.add(registro);
        }
        
        // Filtrar adicionalmente por valores no vacíos y por fecha exacta
        List<ScoutRegistration> registrosFiltrados = registros.stream()
                .filter(r -> r.getScoutId() != null && !r.getScoutId().trim().isEmpty())
                .filter(r -> r.getDriverId() != null && !r.getDriverId().trim().isEmpty())
                .filter(r -> r.getRegistrationDate() != null && r.getRegistrationDate().equals(fecha))
                .collect(Collectors.toList());
        
        // Log para debugging: verificar si hay registros con fechas diferentes
        List<ScoutRegistration> registrosConFechasDiferentes = registros.stream()
                .filter(r -> r.getRegistrationDate() != null && !r.getRegistrationDate().equals(fecha))
                .collect(Collectors.toList());
        if (!registrosConFechasDiferentes.isEmpty()) {
            logger.warn("ADVERTENCIA: Se encontraron {} registros con fechas diferentes a la solicitada {}: {}", 
                    registrosConFechasDiferentes.size(), fecha, 
                    registrosConFechasDiferentes.stream()
                            .map(r -> r.getDriverId() + ":" + r.getRegistrationDate())
                            .collect(Collectors.joining(", ")));
        }
        
        logger.debug("Después del filtro, quedan {} registros para fecha {}", registrosFiltrados.size(), fecha);
        return registrosFiltrados;
    }
    
    public Map<String, Object> obtenerVistaHistorica(int meses, int offset, int limit) {
        logger.info("Obteniendo vista histórica para últimos {} meses, offset: {}, limit: {}", meses, offset, limit);
        
        LocalDate fechaInicio = LocalDate.now().minusMonths(meses);
        LocalDate fechaFin = LocalDate.now();
        
        List<ScoutRegistration> todosRegistros = scoutRegistrationRepository
                .findByRegistrationDateBetween(fechaInicio, fechaFin)
                .stream()
                .filter(ScoutRegistration::getIsMatched)
                .filter(r -> r.getScoutId() != null && !r.getScoutId().trim().isEmpty())
                .filter(r -> r.getDriverId() != null && !r.getDriverId().trim().isEmpty())
                .collect(Collectors.toList());
        
        int total = todosRegistros.size();
        
        List<ScoutRegistration> registrosPaginados = todosRegistros.stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());
        
        List<ScoutWeeklyPaymentViewDTO> vista = procesarRegistrosParaVista(registrosPaginados, fechaInicio, fechaFin);
        
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("data", vista);
        resultado.put("total", total);
        resultado.put("hasMore", (offset + limit) < total);
        
        return resultado;
    }
    
    private List<ScoutWeeklyPaymentViewDTO> procesarRegistrosParaVista(
            List<ScoutRegistration> registros, LocalDate fechaInicio, LocalDate fechaFin) {
        
        if (registros.isEmpty()) {
            return new ArrayList<>();
        }
        
        Map<String, List<ScoutRegistration>> registrosPorScout = registros.stream()
                .collect(Collectors.groupingBy(ScoutRegistration::getScoutId));
        
        Set<String> scoutIds = registrosPorScout.keySet();
        List<Scout> scouts = scoutRepository.findAllById(scoutIds);
        Map<String, String> scoutNames = scouts.stream()
                .collect(Collectors.toMap(Scout::getScoutId, Scout::getScoutName));
        
        List<ScoutWeeklyPaymentViewDTO> vista = new ArrayList<>();
        
        for (Map.Entry<String, List<ScoutRegistration>> entry : registrosPorScout.entrySet()) {
            String scoutId = entry.getKey();
            List<ScoutRegistration> registrosScout = entry.getValue();
            
            // Para vista diaria, solo procesar drivers con registration_date igual a fechaInicio
            // Si fechaInicio == fechaFin, es una vista diaria y debemos filtrar estrictamente
            final LocalDate fechaFiltro = (fechaInicio.equals(fechaFin)) ? fechaInicio : null;
            List<ScoutRegistration> registrosScoutFiltrados = registrosScout;
            if (fechaFiltro != null) {
                logger.debug("Filtrando registros del scout {} para fecha diaria: {}", scoutId, fechaFiltro);
                logger.debug("Registros antes del filtro: {}", registrosScout.size());
                registrosScoutFiltrados = registrosScout.stream()
                        .filter(r -> {
                            boolean matches = r.getRegistrationDate() != null && r.getRegistrationDate().equals(fechaFiltro);
                            if (!matches && r.getRegistrationDate() != null) {
                                logger.debug("Registro descartado: driverId={}, registrationDate={}, fechaFiltro={}", 
                                        r.getDriverId(), r.getRegistrationDate(), fechaFiltro);
                            }
                            return matches;
                        })
                        .collect(Collectors.toList());
                logger.debug("Registros después del filtro: {}", registrosScoutFiltrados.size());
            }
            
            Set<String> driverIdsFiltrados = registrosScoutFiltrados.stream()
                    .map(ScoutRegistration::getDriverId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            
            if (driverIdsFiltrados.isEmpty()) {
                logger.debug("No hay drivers filtrados para scout {}, saltando...", scoutId);
                continue;
            }
            
            logger.debug("Procesando {} drivers para scout {} con fecha filtro {}", 
                    driverIdsFiltrados.size(), scoutId, fechaFiltro);
            
            Map<String, LocalDate> driverHireDates = obtenerHireDates(driverIdsFiltrados);
            // Para vista diaria, asegurarse de que solo se usen fechas del día específico
            Map<String, LocalDate> driverRegistrationDates = registrosScoutFiltrados.stream()
                    .filter(r -> r.getDriverId() != null)
                    .filter(r -> fechaFiltro == null || (r.getRegistrationDate() != null && r.getRegistrationDate().equals(fechaFiltro)))
                    .collect(Collectors.toMap(
                            ScoutRegistration::getDriverId,
                            ScoutRegistration::getRegistrationDate,
                            (date1, date2) -> {
                                // Si es vista diaria, ambas fechas deberían ser iguales, pero por seguridad tomamos la que coincide con fechaFiltro
                                if (fechaFiltro != null) {
                                    if (date1.equals(fechaFiltro)) return date1;
                                    if (date2.equals(fechaFiltro)) return date2;
                                }
                                return date1.isBefore(date2) ? date1 : date2;
                            }
                    ));
            
            List<MilestoneInstance> milestones = milestoneInstanceRepository
                    .findByDriverIdInAndPeriodDays(driverIdsFiltrados, SCOUT_PERIOD_DAYS);
            
            Map<String, List<MilestoneInstance>> milestonesPorDriver = milestones.stream()
                    .collect(Collectors.groupingBy(MilestoneInstance::getDriverId));
            
            Map<String, Boolean> conexionesPorDriver = verificarConexiones(driverIdsFiltrados, driverHireDates);
            Map<String, Integer> registrosPorDia = contarRegistrosPorDia(scoutId, fechaInicio, fechaFin);
            
            Map<Integer, ScoutPaymentConfig> configs = configRepository.findByIsActiveTrue().stream()
                    .filter(c -> c.getMilestoneType() != null)
                    .collect(Collectors.toMap(ScoutPaymentConfig::getMilestoneType, c -> c));
            
            // Obtener todas las instancias de pago en batch para todos los drivers
            Map<String, ScoutPaymentInstance> todasInstanciasPago = obtenerTodasInstanciasPago(scoutId, driverIdsFiltrados);
            
            // Obtener nombres y teléfonos de drivers en batch
            Map<String, String> nombresDrivers = obtenerNombresDrivers(driverIdsFiltrados);
            Map<String, String> telefonosDrivers = obtenerTelefonosDrivers(driverIdsFiltrados);
            
            // Verificar elegibilidad en batch
            Map<String, Boolean> elegibilidadBatch = verificarElegibilidadScoutBatch(scoutId, driverRegistrationDates);
            
            List<DriverWeeklyInfoDTO> driversInfo = new ArrayList<>();
            
            for (String driverId : driverIdsFiltrados) {
                LocalDate registrationDate = driverRegistrationDates.get(driverId);
                LocalDate hireDate = driverHireDates.get(driverId);
                
                if (hireDate == null || registrationDate == null) {
                    logger.debug("Saltando driver {} por falta de hireDate o registrationDate", driverId);
                    continue;
                }
                
                // Para vista diaria, asegurarse de que solo se procesen drivers con la fecha correcta
                if (fechaFiltro != null && !registrationDate.equals(fechaFiltro)) {
                    logger.warn("ADVERTENCIA: Driver {} tiene registrationDate {} diferente a fechaFiltro {}, saltando...", 
                            driverId, registrationDate, fechaFiltro);
                    continue;
                }
                
                List<MilestoneInstance> driverMilestones = milestonesPorDriver.getOrDefault(driverId, new ArrayList<>());
                
                Boolean reachedMilestone1 = driverMilestones.stream()
                        .anyMatch(m -> m.getMilestoneType() != null && m.getMilestoneType() == 1);
                Boolean reachedMilestone5 = driverMilestones.stream()
                        .anyMatch(m -> m.getMilestoneType() != null && m.getMilestoneType() == 5);
                Boolean reachedMilestone25 = driverMilestones.stream()
                        .anyMatch(m -> m.getMilestoneType() != null && m.getMilestoneType() == 25);
                
                LocalDate milestone1Date = driverMilestones.stream()
                        .filter(m -> m.getMilestoneType() != null && m.getMilestoneType() == 1)
                        .map(m -> m.getFulfillmentDate().toLocalDate())
                        .findFirst().orElse(null);
                
                LocalDate milestone5Date = driverMilestones.stream()
                        .filter(m -> m.getMilestoneType() != null && m.getMilestoneType() == 5)
                        .map(m -> m.getFulfillmentDate().toLocalDate())
                        .findFirst().orElse(null);
                
                LocalDate milestone25Date = driverMilestones.stream()
                        .filter(m -> m.getMilestoneType() != null && m.getMilestoneType() == 25)
                        .map(m -> m.getFulfillmentDate().toLocalDate())
                        .findFirst().orElse(null);
                
                Boolean hasConnection = conexionesPorDriver.getOrDefault(driverId, false);
                Integer registrosDia = registrosPorDia.getOrDefault(registrationDate.toString(), 0);
                Boolean scoutReached8Registrations = registrosDia >= 8;
                
                // Obtener elegibilidad del map batch
                String elegibilidadKey = scoutId + "-" + registrationDate;
                boolean elegible = elegibilidadBatch.getOrDefault(elegibilidadKey, false);
                String razon = elegible ? "Elegible" : 
                        "Scout no cumple requisito de 8 registros con conexión el día de registro";
                
                // Obtener instancias de pago del map batch
                Map<Integer, ScoutPaymentInstance> instancesByType = new HashMap<>();
                for (Integer milestoneType : SCOUT_MILESTONE_TYPES) {
                    String instanceKey = scoutId + "-" + driverId + "-" + milestoneType;
                    ScoutPaymentInstance instance = todasInstanciasPago.get(instanceKey);
                    if (instance != null) {
                        instancesByType.put(milestoneType, instance);
                    }
                }
                
                // Obtener información individual de cada milestone
                ScoutPaymentInstance instance1 = instancesByType.get(1);
                ScoutPaymentInstance instance5 = instancesByType.get(5);
                ScoutPaymentInstance instance25 = instancesByType.get(25);
                
                String milestone1Status = instance1 != null ? instance1.getStatus() : (reachedMilestone1 ? "pending" : null);
                // Siempre usar el monto de la configuración actual, no el de la instancia existente (puede estar desactualizado)
                BigDecimal milestone1Amount = reachedMilestone1 && configs.get(1) != null ? 
                        configs.get(1).getAmountScout() : BigDecimal.ZERO;
                Long milestone1InstanceId = instance1 != null ? instance1.getId() : null;
                
                String milestone5Status = instance5 != null ? instance5.getStatus() : (reachedMilestone5 ? "pending" : null);
                // Siempre usar el monto de la configuración actual
                BigDecimal milestone5Amount = reachedMilestone5 && configs.get(5) != null ? 
                        configs.get(5).getAmountScout() : BigDecimal.ZERO;
                Long milestone5InstanceId = instance5 != null ? instance5.getId() : null;
                
                String milestone25Status = instance25 != null ? instance25.getStatus() : (reachedMilestone25 ? "pending" : null);
                // Siempre usar el monto de la configuración actual
                BigDecimal milestone25Amount = reachedMilestone25 && configs.get(25) != null ? 
                        configs.get(25).getAmountScout() : BigDecimal.ZERO;
                Long milestone25InstanceId = instance25 != null ? instance25.getId() : null;
                
                // Calcular estados de expiración para milestones no alcanzados
                // Solo aplicar si el milestone no ha sido alcanzado (reachedMilestoneX == false)
                LocalDate fechaActual = LocalDate.now();
                LocalDate fechaLimite = hireDate.plusDays(SCOUT_PERIOD_DAYS);
                
                String milestone1ExpirationStatus = null;
                if (reachedMilestone1 == null || !reachedMilestone1) {
                    milestone1ExpirationStatus = fechaActual.isBefore(fechaLimite) ? "in_progress" : "expired";
                }
                
                String milestone5ExpirationStatus = null;
                if (reachedMilestone5 == null || !reachedMilestone5) {
                    milestone5ExpirationStatus = fechaActual.isBefore(fechaLimite) ? "in_progress" : "expired";
                }
                
                String milestone25ExpirationStatus = null;
                if (reachedMilestone25 == null || !reachedMilestone25) {
                    milestone25ExpirationStatus = fechaActual.isBefore(fechaLimite) ? "in_progress" : "expired";
                }
                
                // Calcular monto total sumando todos los milestones alcanzados
                BigDecimal totalAmount = BigDecimal.ZERO;
                if (reachedMilestone1 && milestone1Status != null && !milestone1Status.equals("cancelled")) {
                    totalAmount = totalAmount.add(milestone1Amount);
                }
                if (reachedMilestone5 && milestone5Status != null && !milestone5Status.equals("cancelled")) {
                    totalAmount = totalAmount.add(milestone5Amount);
                }
                if (reachedMilestone25 && milestone25Status != null && !milestone25Status.equals("cancelled")) {
                    totalAmount = totalAmount.add(milestone25Amount);
                }
                
                // Determinar estado general
                String generalStatus = "pending";
                boolean hasPending = (milestone1Status != null && milestone1Status.equals("pending")) ||
                                    (milestone5Status != null && milestone5Status.equals("pending")) ||
                                    (milestone25Status != null && milestone25Status.equals("pending"));
                boolean hasPaid = (milestone1Status != null && milestone1Status.equals("paid")) ||
                                 (milestone5Status != null && milestone5Status.equals("paid")) ||
                                 (milestone25Status != null && milestone25Status.equals("paid"));
                boolean allCancelled = (milestone1Status == null || milestone1Status.equals("cancelled")) &&
                                      (milestone5Status == null || milestone5Status.equals("cancelled")) &&
                                      (milestone25Status == null || milestone25Status.equals("cancelled"));
                
                if (allCancelled) {
                    generalStatus = "cancelled";
                } else if (hasPending && hasPaid) {
                    generalStatus = "partial_paid";
                } else if (hasPaid && !hasPending) {
                    generalStatus = "all_paid";
                } else {
                    generalStatus = "pending";
                }
                
                DriverWeeklyInfoDTO driverInfo = new DriverWeeklyInfoDTO();
                driverInfo.setDriverId(driverId);
                driverInfo.setDriverName(nombresDrivers.getOrDefault(driverId, driverId));
                driverInfo.setDriverPhone(telefonosDrivers.get(driverId));
                driverInfo.setRegistrationDate(registrationDate);
                driverInfo.setHireDate(hireDate);
                driverInfo.setHasConnection(hasConnection);
                driverInfo.setReachedMilestone1(reachedMilestone1);
                driverInfo.setReachedMilestone5(reachedMilestone5);
                driverInfo.setReachedMilestone25(reachedMilestone25);
                driverInfo.setMilestone1Date(milestone1Date);
                driverInfo.setMilestone5Date(milestone5Date);
                driverInfo.setMilestone25Date(milestone25Date);
                driverInfo.setScoutReached8Registrations(scoutReached8Registrations);
                driverInfo.setIsEligible(elegible && hasConnection && reachedMilestone1);
                driverInfo.setEligibilityReason(razon);
                driverInfo.setAmount(totalAmount);
                driverInfo.setStatus(generalStatus);
                
                // Campos individuales por milestone
                driverInfo.setMilestone1Status(milestone1Status);
                driverInfo.setMilestone1Amount(milestone1Amount);
                driverInfo.setMilestone1InstanceId(milestone1InstanceId);
                
                driverInfo.setMilestone5Status(milestone5Status);
                driverInfo.setMilestone5Amount(milestone5Amount);
                driverInfo.setMilestone5InstanceId(milestone5InstanceId);
                
                driverInfo.setMilestone25Status(milestone25Status);
                driverInfo.setMilestone25Amount(milestone25Amount);
                driverInfo.setMilestone25InstanceId(milestone25InstanceId);
                
                // Estados de expiración
                driverInfo.setMilestone1ExpirationStatus(milestone1ExpirationStatus);
                driverInfo.setMilestone5ExpirationStatus(milestone5ExpirationStatus);
                driverInfo.setMilestone25ExpirationStatus(milestone25ExpirationStatus);
                
                // Para compatibilidad, mantener instanceId como el primero pendiente o el primero disponible
                if (milestone1InstanceId != null && milestone1Status != null && milestone1Status.equals("pending")) {
                    driverInfo.setInstanceId(milestone1InstanceId);
                } else if (milestone5InstanceId != null && milestone5Status != null && milestone5Status.equals("pending")) {
                    driverInfo.setInstanceId(milestone5InstanceId);
                } else if (milestone25InstanceId != null && milestone25Status != null && milestone25Status.equals("pending")) {
                    driverInfo.setInstanceId(milestone25InstanceId);
                } else {
                    driverInfo.setInstanceId(milestone1InstanceId != null ? milestone1InstanceId : 
                                            (milestone5InstanceId != null ? milestone5InstanceId : milestone25InstanceId));
                }
                
                driversInfo.add(driverInfo);
            }
            
            ScoutWeeklyPaymentViewDTO scoutView = new ScoutWeeklyPaymentViewDTO();
            scoutView.setScoutId(scoutId);
            scoutView.setScoutName(scoutNames.getOrDefault(scoutId, scoutId));
            scoutView.setDrivers(driversInfo);
            
            vista.add(scoutView);
        }
        
        return vista;
    }
}

