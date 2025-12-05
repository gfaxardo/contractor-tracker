package com.yego.contractortracker.service;

import com.yego.contractortracker.dto.DriverOnboardingDTO;
import com.yego.contractortracker.dto.EvolutionMetricsDTO;
import com.yego.contractortracker.dto.MilestoneInstanceDTO;
import com.yego.contractortracker.dto.OnboardingFilterDTO;
import com.yego.contractortracker.dto.PaginatedResponse;
import com.yego.contractortracker.entity.ContractorTrackingHistory;
import com.yego.contractortracker.entity.MilestoneInstance;
import com.yego.contractortracker.repository.ContractorTrackingHistoryRepository;
import com.yego.contractortracker.repository.MilestoneInstanceRepository;
import com.yego.contractortracker.service.MilestoneTrackingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.*;
import java.util.Set;
import java.util.HashSet;

@Service
public class OnboardingService {
    
    private static final Logger logger = LoggerFactory.getLogger(OnboardingService.class);
    private static final String DEFAULT_PARK_ID = "08e20910d81d42658d4334d3f6d10ac0";
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private ContractorTrackingHistoryRepository trackingHistoryRepository;
    
    @Autowired
    private MilestoneTrackingService milestoneTrackingService;
    
    @Autowired
    private MilestoneInstanceRepository milestoneInstanceRepository;
    
    @Autowired
    private YangoTransactionService yangoTransactionService;
    
    @Transactional
    public void calculateAndSaveMetrics(String parkId) {
        parkId = parkId != null && !parkId.isEmpty() ? parkId : DEFAULT_PARK_ID;
        LocalDateTime calculationDate = LocalDateTime.now();
        
        logger.info("Iniciando cálculo de métricas históricas completas para parkId: {}", parkId);
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        sql.append("  d.driver_id, ");
        sql.append("  d.park_id, ");
        sql.append("  d.hire_date, ");
        sql.append("  COALESCE(SUM(CASE WHEN TO_DATE(sd.date_file, 'DD-MM-YYYY') >= d.hire_date AND TO_DATE(sd.date_file, 'DD-MM-YYYY') < d.hire_date + INTERVAL '14 days' THEN sd.sum_work_time_seconds ELSE NULL END), NULL) as sum_work_time_seconds, ");
        sql.append("  COALESCE(SUM(CASE WHEN TO_DATE(sd.date_file, 'DD-MM-YYYY') >= d.hire_date AND TO_DATE(sd.date_file, 'DD-MM-YYYY') < d.hire_date + INTERVAL '14 days' THEN COALESCE(sd.count_orders_completed, 0) ELSE 0 END), 0) as total_trips_historical, ");
        sql.append("  CASE WHEN COUNT(CASE WHEN TO_DATE(sd.date_file, 'DD-MM-YYYY') >= d.hire_date AND TO_DATE(sd.date_file, 'DD-MM-YYYY') < d.hire_date + INTERVAL '14 days' AND COALESCE(sd.sum_work_time_seconds, 0) > 0 THEN 1 END) > 0 THEN true ELSE false END as has_historical_connection ");
        sql.append("FROM drivers d ");
        sql.append("LEFT JOIN summary_daily sd ON sd.driver_id = d.driver_id ");
        sql.append("WHERE d.park_id = ? ");
        sql.append("GROUP BY d.driver_id, d.park_id, d.hire_date ");
        sql.append("ORDER BY d.driver_id");
        
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), parkId);
            logger.info("Calculando métricas para {} drivers", rows.size());
            
            if (rows.isEmpty()) {
                logger.warn("No se encontraron drivers para parkId: {}", parkId);
                return;
            }
            
            List<String> driverIds = new ArrayList<>();
            Map<String, LocalDate> hireDates = new HashMap<>();
            Map<String, Map<String, Object>> basicMetrics = new HashMap<>();
            
            for (Map<String, Object> row : rows) {
                String driverId = (String) row.get("driver_id");
                driverIds.add(driverId);
                
                Object hireDateObj = row.get("hire_date");
                LocalDate hireDate = null;
                if (hireDateObj instanceof java.sql.Date) {
                    hireDate = ((java.sql.Date) hireDateObj).toLocalDate();
                } else if (hireDateObj instanceof java.sql.Timestamp) {
                    hireDate = ((java.sql.Timestamp) hireDateObj).toLocalDateTime().toLocalDate();
                } else if (hireDateObj instanceof LocalDate) {
                    hireDate = (LocalDate) hireDateObj;
                }
                hireDates.put(driverId, hireDate);
                basicMetrics.put(driverId, row);
            }
            
            Map<String, Map<String, Object>> detailedMetrics = calcularMetricasDetalladas(driverIds, hireDates);
            Map<String, Map<String, Object>> leadData = obtenerDatosLeads(driverIds);
            Map<String, Map<String, Object>> scoutData = obtenerDatosScouts(driverIds);
            Map<String, String> acquisitionChannels = obtenerCanalesAdquisicion(driverIds);
            
            int savedCount = 0;
            for (String driverId : driverIds) {
                Map<String, Object> basic = basicMetrics.get(driverId);
                Map<String, Object> detailed = detailedMetrics.getOrDefault(driverId, new HashMap<>());
                Map<String, Object> lead = leadData.getOrDefault(driverId, new HashMap<>());
                Map<String, Object> scout = scoutData.getOrDefault(driverId, new HashMap<>());
                LocalDate hireDate = hireDates.get(driverId);
                
                String driverParkId = (String) basic.get("park_id");
                
                Long sumWorkTimeSeconds = null;
                Object sumWorkTimeObj = basic.get("sum_work_time_seconds");
                if (sumWorkTimeObj != null && sumWorkTimeObj instanceof Number) {
                    sumWorkTimeSeconds = ((Number) sumWorkTimeObj).longValue();
                }
                
                Integer totalTripsHistorical = 0;
                Object totalTripsObj = basic.get("total_trips_historical");
                if (totalTripsObj != null && totalTripsObj instanceof Number) {
                    totalTripsHistorical = ((Number) totalTripsObj).intValue();
                }
                
                Boolean hasHistoricalConnection = false;
                Object hasConnectionObj = basic.get("has_historical_connection");
                if (hasConnectionObj != null) {
                    if (hasConnectionObj instanceof Boolean) {
                        hasHistoricalConnection = (Boolean) hasConnectionObj;
                    } else if (hasConnectionObj instanceof String) {
                        hasHistoricalConnection = Boolean.parseBoolean((String) hasConnectionObj);
                    }
                }
                
                boolean statusRegistered = (sumWorkTimeSeconds == null || sumWorkTimeSeconds == 0) && totalTripsHistorical == 0;
                boolean statusConnected = sumWorkTimeSeconds != null && sumWorkTimeSeconds >= 1;
                boolean statusWithTrips = totalTripsHistorical >= 1;
                
                LocalDate primeraConexion = obtenerFecha(detailed.get("primeraConexion"));
                LocalDate primerViaje = obtenerFecha(detailed.get("primerViaje"));
                Integer diasActivos = obtenerInteger(detailed.get("diasActivos"));
                Integer diasConectados = obtenerInteger(detailed.get("diasConectados"));
                
                Integer diasRegistroAConexion = null;
                if (hireDate != null && primeraConexion != null) {
                    diasRegistroAConexion = (int) java.time.temporal.ChronoUnit.DAYS.between(hireDate, primeraConexion);
                }
                
                Integer diasConexionAViaje = null;
                Boolean primeraConexionTieneViajes = obtenerBoolean(detailed.get("primeraConexionTieneViajes"));
                if (primeraConexion != null && primerViaje != null) {
                    if (primeraConexionTieneViajes != null && primeraConexionTieneViajes) {
                        diasConexionAViaje = 0;
                    } else {
                        diasConexionAViaje = (int) java.time.temporal.ChronoUnit.DAYS.between(primeraConexion, primerViaje);
                    }
                }
                
                Integer diasPrimerViajeA25Viajes = null;
                if (primerViaje != null && hireDate != null) {
                    LocalDate fecha25Viajes = calcularFecha25Viajes(driverId, primerViaje, hireDate);
                    if (fecha25Viajes != null) {
                        diasPrimerViajeA25Viajes = (int) java.time.temporal.ChronoUnit.DAYS.between(primerViaje, fecha25Viajes);
                    }
                }
                
                Double tasaConversionConexion = null;
                if (diasConectados != null && diasConectados > 0 && diasActivos != null) {
                    tasaConversionConexion = (diasActivos.doubleValue() / diasConectados.doubleValue()) * 100.0;
                }
                
                Boolean tieneLead = obtenerBoolean(lead.get("tieneLead"));
                Boolean tieneScout = obtenerBoolean(scout.get("tieneScout"));
                
                Boolean matchScoreBajo = false;
                Double matchScore = obtenerDouble(lead.get("matchScore"));
                Double scoutMatchScore = obtenerDouble(scout.get("scoutMatchScore"));
                if ((matchScore != null && matchScore < 0.7) || (scoutMatchScore != null && scoutMatchScore < 0.7)) {
                    matchScoreBajo = true;
                }
                
                Boolean tieneInconsistencias = false;
                if (tieneLead != null && tieneLead && (tieneScout == null || !tieneScout)) {
                    tieneInconsistencias = true;
                }
                if (tieneScout != null && tieneScout && (tieneLead == null || !tieneLead)) {
                    tieneInconsistencias = true;
                }
                
                ContractorTrackingHistory history = new ContractorTrackingHistory();
                history.setDriverId(driverId);
                history.setParkId(driverParkId);
                history.setCalculationDate(calculationDate);
                history.setTotalTripsHistorical(totalTripsHistorical);
                history.setSumWorkTimeSeconds(sumWorkTimeSeconds);
                history.setHasHistoricalConnection(hasHistoricalConnection);
                history.setStatusRegistered(statusRegistered);
                history.setStatusConnected(statusConnected);
                history.setStatusWithTrips(statusWithTrips);
                history.setAcquisitionChannel(acquisitionChannels.get(driverId));
                history.setPrimeraConexionDate(primeraConexion);
                history.setPrimerViajeDate(primerViaje);
                history.setDiasActivos(diasActivos);
                history.setDiasConectados(diasConectados);
                history.setDiasRegistroAConexion(diasRegistroAConexion);
                history.setDiasConexionAViaje(diasConexionAViaje);
                history.setDiasPrimerViajeA25Viajes(diasPrimerViajeA25Viajes);
                history.setTasaConversionConexion(tasaConversionConexion);
                history.setTieneLead(tieneLead);
                history.setTieneScout(tieneScout);
                history.setMatchScoreBajo(matchScoreBajo);
                history.setTieneInconsistencias(tieneInconsistencias);
                
                trackingHistoryRepository.save(history);
                savedCount++;
            }
            
            logger.info("Métricas calculadas y guardadas exitosamente para {} drivers", savedCount);
        } catch (Exception e) {
            logger.error("Error al calcular y guardar métricas para parkId: {}", parkId, e);
            throw e;
        }
    }
    
    private Map<String, Map<String, Object>> calcularMetricasDetalladas(List<String> driverIds, Map<String, LocalDate> hireDates) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        if (driverIds.isEmpty()) {
            return result;
        }
        
        try {
            String placeholders = driverIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
            
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ");
            sql.append("  sd.driver_id, ");
            sql.append("  MIN(CASE WHEN COALESCE(sd.sum_work_time_seconds, 0) > 0 THEN TO_DATE(sd.date_file, 'DD-MM-YYYY') END) as primera_conexion_date, ");
            sql.append("  MIN(CASE WHEN COALESCE(sd.count_orders_completed, 0) > 0 THEN TO_DATE(sd.date_file, 'DD-MM-YYYY') END) as primer_viaje_date, ");
            sql.append("  COUNT(DISTINCT CASE WHEN COALESCE(sd.count_orders_completed, 0) > 0 THEN TO_DATE(sd.date_file, 'DD-MM-YYYY') END) as dias_activos, ");
            sql.append("  COUNT(DISTINCT CASE WHEN COALESCE(sd.sum_work_time_seconds, 0) > 0 THEN TO_DATE(sd.date_file, 'DD-MM-YYYY') END) as dias_conectados ");
            sql.append("FROM summary_daily sd ");
            sql.append("INNER JOIN drivers d ON sd.driver_id = d.driver_id ");
            sql.append("WHERE sd.driver_id IN (").append(placeholders).append(") ");
            sql.append("  AND TO_DATE(sd.date_file, 'DD-MM-YYYY') >= d.hire_date ");
            sql.append("  AND TO_DATE(sd.date_file, 'DD-MM-YYYY') < d.hire_date + INTERVAL '14 days' ");
            sql.append("GROUP BY sd.driver_id");
            
            List<Object> params = new ArrayList<>(driverIds);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            
            for (Map<String, Object> row : rows) {
                String driverId = (String) row.get("driver_id");
                Map<String, Object> metrics = new HashMap<>();
                metrics.put("primeraConexion", row.get("primera_conexion_date"));
                metrics.put("primerViaje", row.get("primer_viaje_date"));
                metrics.put("diasActivos", row.get("dias_activos"));
                metrics.put("diasConectados", row.get("dias_conectados"));
                result.put(driverId, metrics);
            }
            
            StringBuilder sqlPrimeraConexion = new StringBuilder();
            sqlPrimeraConexion.append("SELECT ");
            sqlPrimeraConexion.append("  primera.driver_id, ");
            sqlPrimeraConexion.append("  CASE WHEN COALESCE(sd.count_orders_completed, 0) > 0 THEN 1 ELSE 0 END as tiene_viajes ");
            sqlPrimeraConexion.append("FROM ( ");
            sqlPrimeraConexion.append("  SELECT sd2.driver_id, MIN(CASE WHEN COALESCE(sd2.sum_work_time_seconds, 0) > 0 THEN TO_DATE(sd2.date_file, 'DD-MM-YYYY') END) as primera_conexion ");
            sqlPrimeraConexion.append("  FROM summary_daily sd2 ");
            sqlPrimeraConexion.append("  INNER JOIN drivers d2 ON sd2.driver_id = d2.driver_id ");
            sqlPrimeraConexion.append("  WHERE sd2.driver_id IN (").append(placeholders).append(") ");
            sqlPrimeraConexion.append("    AND TO_DATE(sd2.date_file, 'DD-MM-YYYY') >= d2.hire_date ");
            sqlPrimeraConexion.append("    AND TO_DATE(sd2.date_file, 'DD-MM-YYYY') < d2.hire_date + INTERVAL '14 days' ");
            sqlPrimeraConexion.append("  GROUP BY sd2.driver_id ");
            sqlPrimeraConexion.append(") primera ");
            sqlPrimeraConexion.append("LEFT JOIN summary_daily sd ON sd.driver_id = primera.driver_id AND TO_DATE(sd.date_file, 'DD-MM-YYYY') = primera.primera_conexion ");
            sqlPrimeraConexion.append("WHERE primera.driver_id IN (").append(placeholders).append(")");
            
            List<Object> paramsPrimeraConexion = new ArrayList<>(driverIds);
            paramsPrimeraConexion.addAll(driverIds);
            List<Map<String, Object>> rowsPrimeraConexion = jdbcTemplate.queryForList(sqlPrimeraConexion.toString(), paramsPrimeraConexion.toArray());
            
            for (Map<String, Object> row : rowsPrimeraConexion) {
                String driverId = (String) row.get("driver_id");
                Map<String, Object> metrics = result.getOrDefault(driverId, new HashMap<>());
                Object tieneViajesObj = row.get("tiene_viajes");
                if (tieneViajesObj != null && tieneViajesObj instanceof Number) {
                    metrics.put("primeraConexionTieneViajes", ((Number) tieneViajesObj).intValue() > 0);
                } else {
                    metrics.put("primeraConexionTieneViajes", false);
                }
                result.put(driverId, metrics);
            }
        } catch (Exception e) {
            logger.error("Error al calcular métricas detalladas", e);
        }
        
        return result;
    }
    
    private Map<String, Map<String, Object>> obtenerDatosLeads(List<String> driverIds) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        if (driverIds.isEmpty()) {
            return result;
        }
        
        try {
            String placeholders = driverIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
            String sql = "SELECT driver_id, lead_created_at, match_score, is_manual FROM lead_matches WHERE driver_id IN (" + placeholders + ") AND is_discarded = false ORDER BY driver_id, matched_at DESC";
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, driverIds.toArray());
            Map<String, Map<String, Object>> latestByDriver = new HashMap<>();
            
            for (Map<String, Object> row : rows) {
                String driverId = (String) row.get("driver_id");
                if (!latestByDriver.containsKey(driverId)) {
                    latestByDriver.put(driverId, row);
                }
            }
            
            for (Map.Entry<String, Map<String, Object>> entry : latestByDriver.entrySet()) {
                Map<String, Object> data = new HashMap<>();
                data.put("tieneLead", true);
                data.put("leadCreatedAt", entry.getValue().get("lead_created_at"));
                data.put("matchScore", entry.getValue().get("match_score"));
                data.put("isManual", entry.getValue().get("is_manual"));
                result.put(entry.getKey(), data);
            }
        } catch (Exception e) {
            logger.error("Error al obtener datos de leads", e);
        }
        
        return result;
    }
    
    private Map<String, Map<String, Object>> obtenerDatosScouts(List<String> driverIds) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        if (driverIds.isEmpty()) {
            return result;
        }
        
        try {
            String placeholders = driverIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
            String sql = "SELECT sr.driver_id, sr.scout_id, sr.registration_date, sr.match_score, s.scout_name " +
                        "FROM scout_registrations sr " +
                        "LEFT JOIN scouts s ON sr.scout_id = s.scout_id " +
                        "WHERE sr.driver_id IN (" + placeholders + ") AND sr.is_matched = true AND sr.driver_id IS NOT NULL " +
                        "ORDER BY sr.driver_id, sr.registration_date DESC";
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, driverIds.toArray());
            Map<String, Map<String, Object>> latestByDriver = new HashMap<>();
            
            for (Map<String, Object> row : rows) {
                String driverId = (String) row.get("driver_id");
                if (driverId != null && !latestByDriver.containsKey(driverId)) {
                    latestByDriver.put(driverId, row);
                }
            }
            
            for (Map.Entry<String, Map<String, Object>> entry : latestByDriver.entrySet()) {
                Map<String, Object> data = new HashMap<>();
                data.put("tieneScout", true);
                data.put("scoutId", entry.getValue().get("scout_id"));
                data.put("scoutName", entry.getValue().get("scout_name"));
                data.put("scoutRegistrationDate", entry.getValue().get("registration_date"));
                data.put("scoutMatchScore", entry.getValue().get("match_score"));
                result.put(entry.getKey(), data);
            }
        } catch (Exception e) {
            logger.error("Error al obtener datos de scouts", e);
        }
        
        return result;
    }
    
    private Map<String, String> obtenerCanalesAdquisicion(List<String> driverIds) {
        Map<String, String> result = new HashMap<>();
        if (driverIds.isEmpty()) {
            return result;
        }
        
        try {
            String placeholders = driverIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
            String sql = "SELECT DISTINCT ON (driver_id) driver_id, acquisition_channel " +
                        "FROM contractor_tracking_history " +
                        "WHERE driver_id IN (" + placeholders + ") " +
                        "ORDER BY driver_id, calculation_date DESC";
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, driverIds.toArray());
            for (Map<String, Object> row : rows) {
                String driverId = (String) row.get("driver_id");
                String channel = (String) row.get("acquisition_channel");
                if (driverId != null && channel != null) {
                    result.put(driverId, channel);
                }
            }
        } catch (Exception e) {
            logger.error("Error al obtener canales de adquisición", e);
        }
        
        return result;
    }
    
    private LocalDate obtenerFecha(Object obj) {
        if (obj == null) return null;
        if (obj instanceof java.sql.Date) {
            return ((java.sql.Date) obj).toLocalDate();
        } else if (obj instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) obj).toLocalDateTime().toLocalDate();
        } else if (obj instanceof LocalDate) {
            return (LocalDate) obj;
        }
        return null;
    }
    
    private Integer obtenerInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        return null;
    }
    
    private Double obtenerDouble(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        return null;
    }
    
    private Boolean obtenerBoolean(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        } else if (obj instanceof String) {
            return Boolean.parseBoolean((String) obj);
        } else if (obj instanceof Number) {
            return ((Number) obj).intValue() > 0;
        }
        return null;
    }
    
    @Transactional(readOnly = true)
    public List<DriverOnboardingDTO> getOnboarding14d(OnboardingFilterDTO filter) {
        String parkId = filter.getParkId() != null && !filter.getParkId().isEmpty() 
            ? filter.getParkId() 
            : DEFAULT_PARK_ID;
        
        // Si se filtra por semana ISO, siempre usar datos en tiempo real para obtener datos actualizados
        if (filter.getWeekISO() != null && !filter.getWeekISO().isEmpty()) {
            logger.info("Filtro por semana ISO detectado ({}), calculando datos en tiempo real para obtener datos actualizados...", filter.getWeekISO());
            return getOnboarding14dRealTime(filter);
        }
        
        long countWithHistory = trackingHistoryRepository.count();
        logger.info("Total de registros en tabla histórica: {}", countWithHistory);
        
        if (countWithHistory == 0) {
            logger.info("Tabla histórica vacía, calculando en tiempo real...");
            return getOnboarding14dRealTime(filter);
        }
        
        logger.info("Leyendo datos históricos para parkId: {}", parkId);
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        sql.append("  d.driver_id, ");
        sql.append("  d.park_id, ");
        sql.append("  d.full_name, ");
        sql.append("  d.phone, ");
        sql.append("  d.hire_date, ");
        sql.append("  d.license_number, ");
        sql.append("  cth.total_trips_historical, ");
        sql.append("  cth.sum_work_time_seconds, ");
        sql.append("  cth.has_historical_connection, ");
        sql.append("  cth.acquisition_channel, ");
        sql.append("  cth.primera_conexion_date, ");
        sql.append("  cth.primer_viaje_date, ");
        sql.append("  cth.dias_activos, ");
        sql.append("  cth.dias_conectados, ");
        sql.append("  cth.dias_registro_a_conexion, ");
        sql.append("  cth.dias_conexion_a_viaje, ");
        sql.append("  cth.dias_primer_viaje_a_25_viajes, ");
        sql.append("  cth.tasa_conversion_conexion, ");
        sql.append("  cth.tiene_lead, ");
        sql.append("  cth.tiene_scout, ");
        sql.append("  cth.match_score_bajo, ");
        sql.append("  cth.tiene_inconsistencias, ");
        sql.append("  lm.lead_created_at, ");
        sql.append("  lm.match_score, ");
        sql.append("  lm.is_manual, ");
        sql.append("  sr.scout_id, ");
        sql.append("  sr.scout_name, ");
        sql.append("  sr.scout_registration_date, ");
        sql.append("  sr.scout_match_score ");
        sql.append("FROM drivers d ");
        sql.append("LEFT JOIN ( ");
        sql.append("  SELECT DISTINCT ON (driver_id) * ");
        sql.append("  FROM contractor_tracking_history ");
        sql.append("  ORDER BY driver_id, calculation_date DESC ");
        sql.append(") cth ON cth.driver_id = d.driver_id ");
        sql.append("LEFT JOIN ( ");
        sql.append("  SELECT DISTINCT ON (driver_id) driver_id, lead_created_at, match_score, is_manual ");
        sql.append("  FROM lead_matches ");
        sql.append("  WHERE COALESCE(is_discarded, false) = false ");
        sql.append("  ORDER BY driver_id, matched_at DESC ");
        sql.append(") lm ON lm.driver_id = d.driver_id ");
        sql.append("LEFT JOIN ( ");
        sql.append("  SELECT DISTINCT ON (sr.driver_id) ");
        sql.append("    sr.driver_id, ");
        sql.append("    sr.scout_id, ");
        sql.append("    sr.registration_date as scout_registration_date, ");
        sql.append("    sr.match_score as scout_match_score, ");
        sql.append("    s.scout_name ");
        sql.append("  FROM scout_registrations sr ");
        sql.append("  LEFT JOIN scouts s ON sr.scout_id = s.scout_id ");
        sql.append("  WHERE sr.driver_id IS NOT NULL ");
        sql.append("    AND sr.is_matched = true ");
        sql.append("  ORDER BY sr.driver_id, sr.registration_date DESC ");
        sql.append(") sr ON sr.driver_id = d.driver_id ");
        sql.append("WHERE d.park_id = ? ");
        
        List<Object> params = new ArrayList<>();
        params.add(parkId);
        
        if (filter.getStartDateFrom() != null) {
            sql.append("  AND d.hire_date >= ? ");
            params.add(filter.getStartDateFrom());
        }
        
        if (filter.getStartDateTo() != null) {
            sql.append("  AND d.hire_date <= ? ");
            params.add(filter.getStartDateTo());
        }
        
        if (filter.getChannel() != null && !filter.getChannel().isEmpty()) {
            if ("cabinet".equalsIgnoreCase(filter.getChannel())) {
                sql.append("  AND cth.acquisition_channel = 'cabinet' ");
            } else if ("otros".equalsIgnoreCase(filter.getChannel())) {
                sql.append("  AND (cth.acquisition_channel IS NULL OR cth.acquisition_channel != 'cabinet') ");
            }
        }
        
        sql.append("ORDER BY d.hire_date DESC, d.driver_id");
        
        String finalSql = sql.toString();
        logger.debug("Ejecutando SQL: {}", finalSql);
        logger.debug("Parámetros: {}", params);
        
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(finalSql, params.toArray());
            logger.debug("Consulta ejecutada exitosamente. Filas retornadas: {}", rows.size());
            
            boolean hasValidHistoricalData = false;
            int driversWithData = 0;
            for (Map<String, Object> row : rows) {
                Object totalTrips = row.get("total_trips_historical");
                Object sumWorkTime = row.get("sum_work_time_seconds");
                
                // Verificar que tenga valores válidos (no null y no todos ceros)
                boolean hasTrips = totalTrips != null && totalTrips instanceof Number && ((Number) totalTrips).intValue() > 0;
                boolean hasWorkTime = sumWorkTime != null && sumWorkTime instanceof Number && ((Number) sumWorkTime).longValue() > 0;
                
                if (hasTrips || hasWorkTime) {
                    hasValidHistoricalData = true;
                    driversWithData++;
                }
            }
            
            logger.debug("Drivers con datos históricos válidos: {}/{}", driversWithData, rows.size());
            
            if (!hasValidHistoricalData && rows.size() > 0) {
                logger.info("No se encontraron datos históricos válidos para los {} drivers consultados, calculando en tiempo real...", rows.size());
                return getOnboarding14dRealTime(filter);
            }
        
            List<DriverOnboardingDTO> drivers = new ArrayList<>();
        
            for (Map<String, Object> row : rows) {
                DriverOnboardingDTO driver = new DriverOnboardingDTO();
                driver.setDriverId((String) row.get("driver_id"));
                driver.setParkId((String) row.get("park_id"));
                driver.setChannel(null);
                driver.setFullName((String) row.get("full_name"));
                driver.setPhone((String) row.get("phone"));
                driver.setLicenseNumber((String) row.get("license_number"));
                
                Object hireDateObj = row.get("hire_date");
                if (hireDateObj instanceof java.sql.Date) {
                    driver.setStartDate(((java.sql.Date) hireDateObj).toLocalDate());
                } else if (hireDateObj instanceof java.sql.Timestamp) {
                    driver.setStartDate(((java.sql.Timestamp) hireDateObj).toLocalDateTime().toLocalDate());
                } else if (hireDateObj instanceof LocalDate) {
                    driver.setStartDate((LocalDate) hireDateObj);
                }
                
                Object totalTripsObj = row.get("total_trips_historical");
                if (totalTripsObj != null) {
                    if (totalTripsObj instanceof Number) {
                        driver.setTotalTrips14d(((Number) totalTripsObj).intValue());
                    } else {
                        driver.setTotalTrips14d(0);
                    }
                } else {
                    driver.setTotalTrips14d(0);
                }
                
                driver.setTotalOnlineTime14d(null);
                
                Object sumWorkTimeObj = row.get("sum_work_time_seconds");
                if (sumWorkTimeObj != null) {
                    if (sumWorkTimeObj instanceof Number) {
                        Long value = ((Number) sumWorkTimeObj).longValue();
                        driver.setSumWorkTimeSeconds(value);
                    } else {
                        driver.setSumWorkTimeSeconds(null);
                    }
                } else {
                    driver.setSumWorkTimeSeconds(null);
                }
                
                Object hasHistoricalConnectionObj = row.get("has_historical_connection");
                if (hasHistoricalConnectionObj != null) {
                    if (hasHistoricalConnectionObj instanceof Boolean) {
                        driver.setHasHistoricalConnection((Boolean) hasHistoricalConnectionObj);
                    } else if (hasHistoricalConnectionObj instanceof String) {
                        driver.setHasHistoricalConnection(Boolean.parseBoolean((String) hasHistoricalConnectionObj));
                    } else {
                        driver.setHasHistoricalConnection(false);
                    }
                } else {
                    driver.setHasHistoricalConnection(false);
                }
                
                calcularStatus14d(driver);
                
                Object leadCreatedAtObj = row.get("lead_created_at");
                if (leadCreatedAtObj != null) {
                    if (leadCreatedAtObj instanceof java.sql.Date) {
                        driver.setLeadCreatedAt(((java.sql.Date) leadCreatedAtObj).toLocalDate());
                    } else if (leadCreatedAtObj instanceof LocalDate) {
                        driver.setLeadCreatedAt((LocalDate) leadCreatedAtObj);
                    }
                }
                
                Object matchScoreObj = row.get("match_score");
                if (matchScoreObj != null) {
                    if (matchScoreObj instanceof Number) {
                        driver.setMatchScore(((Number) matchScoreObj).doubleValue());
                    }
                }
                
                Object isManualObj = row.get("is_manual");
                if (isManualObj != null) {
                    if (isManualObj instanceof Boolean) {
                        driver.setMatchManual((Boolean) isManualObj);
                    } else if (isManualObj instanceof String) {
                        driver.setMatchManual(Boolean.parseBoolean((String) isManualObj));
                    }
                }
                
                Object scoutIdObj = row.get("scout_id");
                if (scoutIdObj != null) {
                    driver.setScoutId((String) scoutIdObj);
                    driver.setHasScoutRegistration(true);
                } else {
                    driver.setHasScoutRegistration(false);
                }
                
                Object scoutNameObj = row.get("scout_name");
                if (scoutNameObj != null) {
                    driver.setScoutName((String) scoutNameObj);
                }
                
                Object scoutRegistrationDateObj = row.get("scout_registration_date");
                if (scoutRegistrationDateObj != null) {
                    if (scoutRegistrationDateObj instanceof java.sql.Date) {
                        driver.setScoutRegistrationDate(((java.sql.Date) scoutRegistrationDateObj).toLocalDate());
                    } else if (scoutRegistrationDateObj instanceof LocalDate) {
                        driver.setScoutRegistrationDate((LocalDate) scoutRegistrationDateObj);
                    }
                }
                
                Object scoutMatchScoreObj = row.get("scout_match_score");
                if (scoutMatchScoreObj != null) {
                    if (scoutMatchScoreObj instanceof Number) {
                        driver.setScoutMatchScore(((Number) scoutMatchScoreObj).doubleValue());
                    }
                }
                
                Object channelObj = row.get("acquisition_channel");
                if (channelObj != null) {
                    driver.setChannel((String) channelObj);
                }
                
                Object primeraConexionObj = row.get("primera_conexion_date");
                if (primeraConexionObj != null) {
                    driver.setPrimeraConexionDate(obtenerFecha(primeraConexionObj));
                }
                
                Object primerViajeObj = row.get("primer_viaje_date");
                if (primerViajeObj != null) {
                    driver.setPrimerViajeDate(obtenerFecha(primerViajeObj));
                }
                
                Object diasActivosObj = row.get("dias_activos");
                if (diasActivosObj != null) {
                    driver.setDiasActivos(obtenerInteger(diasActivosObj));
                }
                
                Object diasConectadosObj = row.get("dias_conectados");
                if (diasConectadosObj != null) {
                    driver.setDiasConectados(obtenerInteger(diasConectadosObj));
                }
                
                Object diasRegistroAConexionObj = row.get("dias_registro_a_conexion");
                if (diasRegistroAConexionObj != null) {
                    driver.setDiasRegistroAConexion(obtenerInteger(diasRegistroAConexionObj));
                }
                
                Object diasConexionAViajeObj = row.get("dias_conexion_a_viaje");
                if (diasConexionAViajeObj != null) {
                    driver.setDiasConexionAViaje(obtenerInteger(diasConexionAViajeObj));
                }
                
                Object diasPrimerViajeA25ViajesObj = row.get("dias_primer_viaje_a_25_viajes");
                if (diasPrimerViajeA25ViajesObj != null) {
                    driver.setDiasPrimerViajeA25Viajes(obtenerInteger(diasPrimerViajeA25ViajesObj));
                }
                
                Object tasaConversionObj = row.get("tasa_conversion_conexion");
                if (tasaConversionObj != null) {
                    driver.setTasaConversionConexion(obtenerDouble(tasaConversionObj));
                }
                
                Object tieneLeadObj = row.get("tiene_lead");
                if (tieneLeadObj != null) {
                    driver.setTieneLead(obtenerBoolean(tieneLeadObj));
                }
                
                Object tieneScoutObj = row.get("tiene_scout");
                if (tieneScoutObj != null) {
                    driver.setTieneScout(obtenerBoolean(tieneScoutObj));
                }
                
                Object matchScoreBajoObj = row.get("match_score_bajo");
                if (matchScoreBajoObj != null) {
                    driver.setMatchScoreBajo(obtenerBoolean(matchScoreBajoObj));
                }
                
                Object tieneInconsistenciasObj = row.get("tiene_inconsistencias");
                if (tieneInconsistenciasObj != null) {
                    driver.setTieneInconsistencias(obtenerBoolean(tieneInconsistenciasObj));
                }
                
                driver.setDays(new ArrayList<>());
                
                driver.setMilestones14d(null);
                driver.setMilestones7d(null);
                
                drivers.add(driver);
            }
        
            cargarTransaccionesYango14d(drivers);
        
            return drivers;
        } catch (Exception e) {
            logger.error("Error al leer datos históricos, calculando en tiempo real como fallback", e);
            return getOnboarding14dRealTime(filter);
        }
    }
    
    public PaginatedResponse<DriverOnboardingDTO> getOnboarding14dPaginated(OnboardingFilterDTO filter) {
        boolean useRealTime = (filter.getWeekISO() != null && !filter.getWeekISO().isEmpty()) 
            || trackingHistoryRepository.count() == 0;
        
        if (useRealTime) {
            return getOnboarding14dRealTimePaginated(filter);
        }
        
        return getOnboarding14dFromHistoryPaginated(filter);
    }
    
    @Transactional(readOnly = true)
    private PaginatedResponse<DriverOnboardingDTO> getOnboarding14dFromHistoryPaginated(OnboardingFilterDTO filter) {
        String parkId = filter.getParkId() != null && !filter.getParkId().isEmpty() 
            ? filter.getParkId() 
            : DEFAULT_PARK_ID;
        
        int page = filter.getPage() != null ? filter.getPage() : 0;
        int size = filter.getSize() != null ? filter.getSize() : 50;
        int offset = page * size;
        
        StringBuilder countSql = new StringBuilder();
        countSql.append("SELECT COUNT(DISTINCT d.driver_id) ");
        countSql.append("FROM drivers d ");
        countSql.append("LEFT JOIN ( ");
        countSql.append("  SELECT DISTINCT ON (driver_id) * ");
        countSql.append("  FROM contractor_tracking_history ");
        countSql.append("  ORDER BY driver_id, calculation_date DESC ");
        countSql.append(") cth ON cth.driver_id = d.driver_id ");
        countSql.append("WHERE d.park_id = ? ");
        
        List<Object> countParams = new ArrayList<>();
        countParams.add(parkId);
        
        if (filter.getStartDateFrom() != null) {
            countSql.append("  AND d.hire_date >= ? ");
            countParams.add(filter.getStartDateFrom());
        }
        
        if (filter.getStartDateTo() != null) {
            countSql.append("  AND d.hire_date <= ? ");
            countParams.add(filter.getStartDateTo());
        }
        
        if (filter.getChannel() != null && !filter.getChannel().isEmpty()) {
            if ("cabinet".equalsIgnoreCase(filter.getChannel())) {
                countSql.append("  AND cth.acquisition_channel = 'cabinet' ");
            } else if ("otros".equalsIgnoreCase(filter.getChannel())) {
                countSql.append("  AND (cth.acquisition_channel IS NULL OR cth.acquisition_channel != 'cabinet') ");
            }
        }
        
        long total = jdbcTemplate.queryForObject(countSql.toString(), countParams.toArray(), Long.class);
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        sql.append("  d.driver_id, ");
        sql.append("  d.park_id, ");
        sql.append("  d.full_name, ");
        sql.append("  d.phone, ");
        sql.append("  d.hire_date, ");
        sql.append("  d.license_number, ");
        sql.append("  cth.total_trips_historical, ");
        sql.append("  cth.sum_work_time_seconds, ");
        sql.append("  cth.has_historical_connection, ");
        sql.append("  cth.acquisition_channel, ");
        sql.append("  cth.primera_conexion_date, ");
        sql.append("  cth.primer_viaje_date, ");
        sql.append("  cth.dias_activos, ");
        sql.append("  cth.dias_conectados, ");
        sql.append("  cth.dias_registro_a_conexion, ");
        sql.append("  cth.dias_conexion_a_viaje, ");
        sql.append("  cth.dias_primer_viaje_a_25_viajes, ");
        sql.append("  cth.tasa_conversion_conexion, ");
        sql.append("  cth.tiene_lead, ");
        sql.append("  cth.tiene_scout, ");
        sql.append("  cth.match_score_bajo, ");
        sql.append("  cth.tiene_inconsistencias, ");
        sql.append("  lm.lead_created_at, ");
        sql.append("  lm.match_score, ");
        sql.append("  lm.is_manual, ");
        sql.append("  sr.scout_id, ");
        sql.append("  sr.scout_name, ");
        sql.append("  sr.scout_registration_date, ");
        sql.append("  sr.scout_match_score ");
        sql.append("FROM drivers d ");
        sql.append("LEFT JOIN ( ");
        sql.append("  SELECT DISTINCT ON (driver_id) * ");
        sql.append("  FROM contractor_tracking_history ");
        sql.append("  ORDER BY driver_id, calculation_date DESC ");
        sql.append(") cth ON cth.driver_id = d.driver_id ");
        sql.append("LEFT JOIN ( ");
        sql.append("  SELECT DISTINCT ON (driver_id) driver_id, lead_created_at, match_score, is_manual ");
        sql.append("  FROM lead_matches ");
        sql.append("  WHERE COALESCE(is_discarded, false) = false ");
        sql.append("  ORDER BY driver_id, matched_at DESC ");
        sql.append(") lm ON lm.driver_id = d.driver_id ");
        sql.append("LEFT JOIN ( ");
        sql.append("  SELECT DISTINCT ON (sr.driver_id) ");
        sql.append("    sr.driver_id, ");
        sql.append("    sr.scout_id, ");
        sql.append("    sr.registration_date as scout_registration_date, ");
        sql.append("    sr.match_score as scout_match_score, ");
        sql.append("    s.scout_name ");
        sql.append("  FROM scout_registrations sr ");
        sql.append("  LEFT JOIN scouts s ON sr.scout_id = s.scout_id ");
        sql.append("  WHERE sr.driver_id IS NOT NULL ");
        sql.append("    AND sr.is_matched = true ");
        sql.append("  ORDER BY sr.driver_id, sr.registration_date DESC ");
        sql.append(") sr ON sr.driver_id = d.driver_id ");
        sql.append("WHERE d.park_id = ? ");
        
        List<Object> params = new ArrayList<>();
        params.add(parkId);
        
        if (filter.getStartDateFrom() != null) {
            sql.append("  AND d.hire_date >= ? ");
            params.add(filter.getStartDateFrom());
        }
        
        if (filter.getStartDateTo() != null) {
            sql.append("  AND d.hire_date <= ? ");
            params.add(filter.getStartDateTo());
        }
        
        if (filter.getChannel() != null && !filter.getChannel().isEmpty()) {
            if ("cabinet".equalsIgnoreCase(filter.getChannel())) {
                sql.append("  AND cth.acquisition_channel = 'cabinet' ");
            } else if ("otros".equalsIgnoreCase(filter.getChannel())) {
                sql.append("  AND (cth.acquisition_channel IS NULL OR cth.acquisition_channel != 'cabinet') ");
            }
        }
        
        sql.append("ORDER BY d.hire_date DESC, d.driver_id ");
        sql.append("LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);
        
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            List<DriverOnboardingDTO> drivers = mapearFilasADrivers(rows);
            cargarTransaccionesYango14d(drivers);
            
            int totalPages = (int) Math.ceil((double) total / size);
            boolean hasMore = (page + 1) < totalPages;
            
            return new PaginatedResponse<>(drivers, page, size, total, hasMore, totalPages);
        } catch (Exception e) {
            logger.error("Error al leer datos históricos, calculando en tiempo real como fallback", e);
            return getOnboarding14dRealTimePaginated(filter);
        }
    }
    
    @Transactional(readOnly = true)
    private PaginatedResponse<DriverOnboardingDTO> getOnboarding14dRealTimePaginated(OnboardingFilterDTO filter) {
        String parkId = filter.getParkId() != null && !filter.getParkId().isEmpty() 
            ? filter.getParkId() 
            : DEFAULT_PARK_ID;
        
        int page = filter.getPage() != null ? filter.getPage() : 0;
        int size = filter.getSize() != null ? filter.getSize() : 50;
        int offset = page * size;
        
        long startTime = System.currentTimeMillis();
        logger.info("Leyendo datos pre-calculados desde contractor_tracking_history para parkId: {} (página {}, tamaño {})", parkId, page, size);
        
        StringBuilder countSql = new StringBuilder();
        countSql.append("WITH filtered_drivers AS ( ");
        countSql.append("  SELECT driver_id ");
        countSql.append("  FROM drivers ");
        countSql.append("  WHERE park_id = ? ");
        List<Object> countParams = new ArrayList<>();
        countParams.add(parkId);
        if (filter.getStartDateFrom() != null) {
            countSql.append("    AND hire_date >= ? ");
            countParams.add(filter.getStartDateFrom());
        }
        if (filter.getStartDateTo() != null) {
            countSql.append("    AND hire_date <= ? ");
            countParams.add(filter.getStartDateTo());
        }
        countSql.append(") ");
        countSql.append("SELECT COUNT(*) FROM filtered_drivers");
        
        Long totalObj = jdbcTemplate.queryForObject(countSql.toString(), countParams.toArray(), Long.class);
        long total = totalObj != null ? totalObj : 0L;
        
        StringBuilder sql = new StringBuilder();
        sql.append("WITH filtered_drivers AS ( ");
        sql.append("  SELECT driver_id, park_id, hire_date, full_name, phone, license_number ");
        sql.append("  FROM drivers ");
        sql.append("  WHERE park_id = ? ");
        List<Object> params = new ArrayList<>();
        params.add(parkId);
        if (filter.getStartDateFrom() != null) {
            sql.append("    AND hire_date >= ? ");
            params.add(filter.getStartDateFrom());
        }
        if (filter.getStartDateTo() != null) {
            sql.append("    AND hire_date <= ? ");
            params.add(filter.getStartDateTo());
        }
        sql.append("), latest_tracking AS ( ");
        sql.append("  SELECT DISTINCT ON (cth.driver_id) ");
        sql.append("    cth.driver_id, ");
        sql.append("    cth.park_id, ");
        sql.append("    cth.sum_work_time_seconds, ");
        sql.append("    cth.total_trips_historical, ");
        sql.append("    cth.has_historical_connection, ");
        sql.append("    cth.acquisition_channel ");
        sql.append("  FROM contractor_tracking_history cth ");
        sql.append("  INNER JOIN filtered_drivers fd ON cth.driver_id = fd.driver_id ");
        sql.append("  ORDER BY cth.driver_id, cth.calculation_date DESC ");
        sql.append("), driver_trips_time_fallback AS ( ");
        sql.append("  SELECT ");
        sql.append("    fd.driver_id, ");
        sql.append("    COALESCE(SUM(sd.count_orders_completed), 0) as total_trips_from_sd, ");
        sql.append("    SUM(sd.sum_work_time_seconds) as sum_work_time_from_sd, ");
        sql.append("    CASE WHEN COUNT(CASE WHEN COALESCE(sd.sum_work_time_seconds, 0) > 0 THEN 1 END) > 0 THEN true ELSE false END as has_connection_from_sd ");
        sql.append("  FROM filtered_drivers fd ");
        sql.append("  LEFT JOIN summary_daily sd ON sd.driver_id = fd.driver_id ");
        sql.append("    AND to_date_immutable(sd.date_file) >= fd.hire_date ");
        sql.append("    AND to_date_immutable(sd.date_file) < fd.hire_date + INTERVAL '14 days' ");
        sql.append("  GROUP BY fd.driver_id ");
        sql.append("), latest_lead_match AS ( ");
        sql.append("  SELECT DISTINCT ON (lm.driver_id) ");
        sql.append("    lm.driver_id, ");
        sql.append("    lm.lead_created_at, ");
        sql.append("    lm.match_score, ");
        sql.append("    lm.is_manual ");
        sql.append("  FROM lead_matches lm ");
        sql.append("  INNER JOIN filtered_drivers fd ON lm.driver_id = fd.driver_id ");
        sql.append("  WHERE lm.is_discarded = false ");
        sql.append("  ORDER BY lm.driver_id, lm.matched_at DESC ");
        sql.append("), latest_scout_registration AS ( ");
        sql.append("  SELECT DISTINCT ON (sr.driver_id) ");
        sql.append("    sr.driver_id, ");
        sql.append("    sr.scout_id, ");
        sql.append("    sr.registration_date as scout_registration_date, ");
        sql.append("    sr.match_score as scout_match_score, ");
        sql.append("    s.scout_name ");
        sql.append("  FROM scout_registrations sr ");
        sql.append("  INNER JOIN filtered_drivers fd ON sr.driver_id = fd.driver_id ");
        sql.append("  LEFT JOIN scouts s ON sr.scout_id = s.scout_id ");
        sql.append("  WHERE sr.driver_id IS NOT NULL ");
        sql.append("    AND sr.is_matched = true ");
        sql.append("  ORDER BY sr.driver_id, sr.registration_date DESC ");
        sql.append(") ");
        sql.append("SELECT ");
        sql.append("  fd.driver_id, ");
        sql.append("  fd.park_id, ");
        sql.append("  fd.full_name, ");
        sql.append("  fd.phone, ");
        sql.append("  fd.hire_date, ");
        sql.append("  fd.license_number, ");
        sql.append("  CASE WHEN cth.sum_work_time_seconds IS NOT NULL AND cth.sum_work_time_seconds > 0 THEN cth.sum_work_time_seconds ELSE dtt.sum_work_time_from_sd END as sum_work_time_seconds, ");
        sql.append("  CASE WHEN cth.total_trips_historical IS NOT NULL AND cth.total_trips_historical > 0 THEN cth.total_trips_historical ELSE COALESCE(dtt.total_trips_from_sd, 0) END as total_trips_historical, ");
        sql.append("  CASE WHEN cth.has_historical_connection IS NOT NULL AND cth.has_historical_connection = true THEN cth.has_historical_connection ELSE COALESCE(dtt.has_connection_from_sd, false) END as has_historical_connection, ");
        sql.append("  cth.acquisition_channel, ");
        sql.append("  NULL::date as primera_conexion_date, ");
        sql.append("  NULL::date as primer_viaje_date, ");
        sql.append("  NULL::integer as dias_activos, ");
        sql.append("  NULL::integer as dias_conectados, ");
        sql.append("  NULL::integer as dias_registro_a_conexion, ");
        sql.append("  NULL::integer as dias_conexion_a_viaje, ");
        sql.append("  NULL::integer as dias_primer_viaje_a_25_viajes, ");
        sql.append("  NULL::double precision as tasa_conversion_conexion, ");
        sql.append("  NULL::boolean as tiene_lead, ");
        sql.append("  NULL::boolean as tiene_scout, ");
        sql.append("  NULL::boolean as match_score_bajo, ");
        sql.append("  NULL::boolean as tiene_inconsistencias, ");
        sql.append("  lm.lead_created_at, ");
        sql.append("  lm.match_score, ");
        sql.append("  lm.is_manual, ");
        sql.append("  sr.scout_id, ");
        sql.append("  sr.scout_name, ");
        sql.append("  sr.scout_registration_date, ");
        sql.append("  sr.scout_match_score ");
        sql.append("FROM filtered_drivers fd ");
        sql.append("LEFT JOIN latest_tracking cth ON cth.driver_id = fd.driver_id ");
        sql.append("LEFT JOIN driver_trips_time_fallback dtt ON dtt.driver_id = fd.driver_id ");
        sql.append("LEFT JOIN latest_lead_match lm ON lm.driver_id = fd.driver_id ");
        sql.append("LEFT JOIN latest_scout_registration sr ON sr.driver_id = fd.driver_id ");
        
        if (filter.getChannel() != null && !filter.getChannel().isEmpty()) {
            if ("cabinet".equalsIgnoreCase(filter.getChannel())) {
                sql.append("WHERE cth.acquisition_channel = 'cabinet' ");
            } else if ("otros".equalsIgnoreCase(filter.getChannel())) {
                sql.append("WHERE (cth.acquisition_channel IS NULL OR cth.acquisition_channel != 'cabinet') ");
            }
        }
        
        sql.append("ORDER BY fd.hire_date DESC, fd.driver_id ");
        sql.append("LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);
        
        String finalSql = sql.toString();
        logger.debug("Ejecutando consulta optimizada paginada: página {}, tamaño {}", page, size);
        
        try {
            jdbcTemplate.setQueryTimeout(90);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(finalSql, params.toArray());
            jdbcTemplate.setQueryTimeout(0);
            long executionTime = System.currentTimeMillis() - startTime;
            logger.info("Consulta paginada ejecutada exitosamente en {} ms. Filas retornadas: {}", executionTime, rows.size());
            
            List<DriverOnboardingDTO> drivers = mapearFilasADrivers(rows);
            cargarTransaccionesYango14d(drivers);
            
            int totalPages = (int) Math.ceil((double) total / size);
            boolean hasMore = (page + 1) < totalPages;
            
            return new PaginatedResponse<>(drivers, page, size, total, hasMore, totalPages);
        } catch (Exception e) {
            logger.error("Error al ejecutar consulta SQL paginada en tiempo real", e);
            return new PaginatedResponse<>(new ArrayList<>(), page, size, 0, false, 0);
        }
    }
    
    private List<DriverOnboardingDTO> mapearFilasADrivers(List<Map<String, Object>> rows) {
        List<DriverOnboardingDTO> drivers = new ArrayList<>();
        
        for (Map<String, Object> row : rows) {
            DriverOnboardingDTO driver = new DriverOnboardingDTO();
            driver.setDriverId((String) row.get("driver_id"));
            driver.setParkId((String) row.get("park_id"));
            driver.setChannel(null);
            driver.setFullName((String) row.get("full_name"));
            driver.setPhone((String) row.get("phone"));
            driver.setLicenseNumber((String) row.get("license_number"));
            
            Object hireDateObj = row.get("hire_date");
            if (hireDateObj instanceof java.sql.Date) {
                driver.setStartDate(((java.sql.Date) hireDateObj).toLocalDate());
            } else if (hireDateObj instanceof java.sql.Timestamp) {
                driver.setStartDate(((java.sql.Timestamp) hireDateObj).toLocalDateTime().toLocalDate());
            } else if (hireDateObj instanceof LocalDate) {
                driver.setStartDate((LocalDate) hireDateObj);
            }
            
            driver.setStatus14d(null);
            
            Object totalTripsObj = row.get("total_trips_historical");
            if (totalTripsObj != null) {
                if (totalTripsObj instanceof Number) {
                    driver.setTotalTrips14d(((Number) totalTripsObj).intValue());
                } else {
                    driver.setTotalTrips14d(0);
                }
            } else {
                driver.setTotalTrips14d(0);
            }
            
            driver.setTotalOnlineTime14d(null);
            
            Object sumWorkTimeObj = row.get("sum_work_time_seconds");
            if (sumWorkTimeObj != null) {
                if (sumWorkTimeObj instanceof Number) {
                    driver.setSumWorkTimeSeconds(((Number) sumWorkTimeObj).longValue());
                } else {
                    driver.setSumWorkTimeSeconds(null);
                }
            } else {
                driver.setSumWorkTimeSeconds(null);
            }
            
            Object hasHistoricalConnectionObj = row.get("has_historical_connection");
            if (hasHistoricalConnectionObj != null) {
                if (hasHistoricalConnectionObj instanceof Boolean) {
                    driver.setHasHistoricalConnection((Boolean) hasHistoricalConnectionObj);
                } else if (hasHistoricalConnectionObj instanceof String) {
                    driver.setHasHistoricalConnection(Boolean.parseBoolean((String) hasHistoricalConnectionObj));
                } else {
                    driver.setHasHistoricalConnection(false);
                }
            } else {
                driver.setHasHistoricalConnection(false);
            }
            
            calcularStatus14d(driver);
            
            Object leadCreatedAtObj = row.get("lead_created_at");
            if (leadCreatedAtObj != null) {
                if (leadCreatedAtObj instanceof java.sql.Date) {
                    driver.setLeadCreatedAt(((java.sql.Date) leadCreatedAtObj).toLocalDate());
                } else if (leadCreatedAtObj instanceof LocalDate) {
                    driver.setLeadCreatedAt((LocalDate) leadCreatedAtObj);
                }
            }
            
            Object matchScoreObj = row.get("match_score");
            if (matchScoreObj != null) {
                if (matchScoreObj instanceof Number) {
                    driver.setMatchScore(((Number) matchScoreObj).doubleValue());
                }
            }
            
            Object isManualObj = row.get("is_manual");
            if (isManualObj != null) {
                if (isManualObj instanceof Boolean) {
                    driver.setMatchManual((Boolean) isManualObj);
                } else if (isManualObj instanceof String) {
                    driver.setMatchManual(Boolean.parseBoolean((String) isManualObj));
                }
            }
            
            Object scoutIdObj = row.get("scout_id");
            if (scoutIdObj != null) {
                driver.setScoutId((String) scoutIdObj);
                driver.setHasScoutRegistration(true);
            } else {
                driver.setHasScoutRegistration(false);
            }
            
            Object scoutNameObj = row.get("scout_name");
            if (scoutNameObj != null) {
                driver.setScoutName((String) scoutNameObj);
            }
            
            Object scoutRegistrationDateObj = row.get("scout_registration_date");
            if (scoutRegistrationDateObj != null) {
                if (scoutRegistrationDateObj instanceof java.sql.Date) {
                    driver.setScoutRegistrationDate(((java.sql.Date) scoutRegistrationDateObj).toLocalDate());
                } else if (scoutRegistrationDateObj instanceof LocalDate) {
                    driver.setScoutRegistrationDate((LocalDate) scoutRegistrationDateObj);
                }
            }
            
            Object scoutMatchScoreObj = row.get("scout_match_score");
            if (scoutMatchScoreObj != null) {
                if (scoutMatchScoreObj instanceof Number) {
                    driver.setScoutMatchScore(((Number) scoutMatchScoreObj).doubleValue());
                }
            }
            
            Object channelObj = row.get("acquisition_channel");
            if (channelObj != null) {
                driver.setChannel((String) channelObj);
            }
            
            driver.setDays(new ArrayList<>());
            driver.setMilestones14d(null);
            driver.setMilestones7d(null);
            
            drivers.add(driver);
        }
        
        return drivers;
    }
    
    @Transactional(readOnly = true)
    private List<DriverOnboardingDTO> getOnboarding14dRealTime(OnboardingFilterDTO filter) {
        String parkId = filter.getParkId() != null && !filter.getParkId().isEmpty() 
            ? filter.getParkId() 
            : DEFAULT_PARK_ID;
        
        long startTime = System.currentTimeMillis();
        logger.info("Leyendo datos pre-calculados desde contractor_tracking_history para parkId: {}", parkId);
        
        StringBuilder sql = new StringBuilder();
        sql.append("WITH filtered_drivers AS ( ");
        sql.append("  SELECT driver_id, park_id, hire_date, full_name, phone, license_number ");
        sql.append("  FROM drivers ");
        sql.append("  WHERE park_id = ? ");
        List<Object> params = new ArrayList<>();
        params.add(parkId);
        if (filter.getStartDateFrom() != null) {
            sql.append("    AND hire_date >= ? ");
            params.add(filter.getStartDateFrom());
        }
        if (filter.getStartDateTo() != null) {
            sql.append("    AND hire_date <= ? ");
            params.add(filter.getStartDateTo());
        }
        sql.append("), latest_tracking AS ( ");
        sql.append("  SELECT DISTINCT ON (cth.driver_id) ");
        sql.append("    cth.driver_id, ");
        sql.append("    cth.park_id, ");
        sql.append("    cth.sum_work_time_seconds, ");
        sql.append("    cth.total_trips_historical, ");
        sql.append("    cth.has_historical_connection, ");
        sql.append("    cth.acquisition_channel ");
        sql.append("  FROM contractor_tracking_history cth ");
        sql.append("  INNER JOIN filtered_drivers fd ON cth.driver_id = fd.driver_id ");
        sql.append("  ORDER BY cth.driver_id, cth.calculation_date DESC ");
        sql.append("), driver_trips_time_fallback AS ( ");
        sql.append("  SELECT ");
        sql.append("    fd.driver_id, ");
        sql.append("    COALESCE(SUM(sd.count_orders_completed), 0) as total_trips_from_sd, ");
        sql.append("    SUM(sd.sum_work_time_seconds) as sum_work_time_from_sd, ");
        sql.append("    CASE WHEN COUNT(CASE WHEN COALESCE(sd.sum_work_time_seconds, 0) > 0 THEN 1 END) > 0 THEN true ELSE false END as has_connection_from_sd ");
        sql.append("  FROM filtered_drivers fd ");
        sql.append("  LEFT JOIN summary_daily sd ON sd.driver_id = fd.driver_id ");
        sql.append("    AND to_date_immutable(sd.date_file) >= fd.hire_date ");
        sql.append("    AND to_date_immutable(sd.date_file) < fd.hire_date + INTERVAL '14 days' ");
        sql.append("  GROUP BY fd.driver_id ");
        sql.append("), latest_lead_match AS ( ");
        sql.append("  SELECT DISTINCT ON (lm.driver_id) ");
        sql.append("    lm.driver_id, ");
        sql.append("    lm.lead_created_at, ");
        sql.append("    lm.match_score, ");
        sql.append("    lm.is_manual ");
        sql.append("  FROM lead_matches lm ");
        sql.append("  INNER JOIN filtered_drivers fd ON lm.driver_id = fd.driver_id ");
        sql.append("  WHERE lm.is_discarded = false ");
        sql.append("  ORDER BY lm.driver_id, lm.matched_at DESC ");
        sql.append("), latest_scout_registration AS ( ");
        sql.append("  SELECT DISTINCT ON (sr.driver_id) ");
        sql.append("    sr.driver_id, ");
        sql.append("    sr.scout_id, ");
        sql.append("    sr.registration_date as scout_registration_date, ");
        sql.append("    sr.match_score as scout_match_score, ");
        sql.append("    s.scout_name ");
        sql.append("  FROM scout_registrations sr ");
        sql.append("  INNER JOIN filtered_drivers fd ON sr.driver_id = fd.driver_id ");
        sql.append("  LEFT JOIN scouts s ON sr.scout_id = s.scout_id ");
        sql.append("  WHERE sr.driver_id IS NOT NULL ");
        sql.append("    AND sr.is_matched = true ");
        sql.append("  ORDER BY sr.driver_id, sr.registration_date DESC ");
        sql.append(") ");
        sql.append("SELECT ");
        sql.append("  fd.driver_id, ");
        sql.append("  fd.park_id, ");
        sql.append("  fd.full_name, ");
        sql.append("  fd.phone, ");
        sql.append("  fd.hire_date, ");
        sql.append("  fd.license_number, ");
        sql.append("  CASE WHEN cth.sum_work_time_seconds IS NOT NULL AND cth.sum_work_time_seconds > 0 THEN cth.sum_work_time_seconds ELSE dtt.sum_work_time_from_sd END as sum_work_time_seconds, ");
        sql.append("  CASE WHEN cth.total_trips_historical IS NOT NULL AND cth.total_trips_historical > 0 THEN cth.total_trips_historical ELSE COALESCE(dtt.total_trips_from_sd, 0) END as total_trips_historical, ");
        sql.append("  CASE WHEN cth.has_historical_connection IS NOT NULL AND cth.has_historical_connection = true THEN cth.has_historical_connection ELSE COALESCE(dtt.has_connection_from_sd, false) END as has_historical_connection, ");
        sql.append("  cth.acquisition_channel, ");
        sql.append("  NULL::date as primera_conexion_date, ");
        sql.append("  NULL::date as primer_viaje_date, ");
        sql.append("  NULL::integer as dias_activos, ");
        sql.append("  NULL::integer as dias_conectados, ");
        sql.append("  NULL::integer as dias_registro_a_conexion, ");
        sql.append("  NULL::integer as dias_conexion_a_viaje, ");
        sql.append("  NULL::integer as dias_primer_viaje_a_25_viajes, ");
        sql.append("  NULL::double precision as tasa_conversion_conexion, ");
        sql.append("  NULL::boolean as tiene_lead, ");
        sql.append("  NULL::boolean as tiene_scout, ");
        sql.append("  NULL::boolean as match_score_bajo, ");
        sql.append("  NULL::boolean as tiene_inconsistencias, ");
        sql.append("  lm.lead_created_at, ");
        sql.append("  lm.match_score, ");
        sql.append("  lm.is_manual, ");
        sql.append("  sr.scout_id, ");
        sql.append("  sr.scout_name, ");
        sql.append("  sr.scout_registration_date, ");
        sql.append("  sr.scout_match_score ");
        sql.append("FROM filtered_drivers fd ");
        sql.append("LEFT JOIN latest_tracking cth ON cth.driver_id = fd.driver_id ");
        sql.append("LEFT JOIN driver_trips_time_fallback dtt ON dtt.driver_id = fd.driver_id ");
        sql.append("LEFT JOIN latest_lead_match lm ON lm.driver_id = fd.driver_id ");
        sql.append("LEFT JOIN latest_scout_registration sr ON sr.driver_id = fd.driver_id ");
        
        if (filter.getChannel() != null && !filter.getChannel().isEmpty()) {
            if ("cabinet".equalsIgnoreCase(filter.getChannel())) {
                sql.append("WHERE cth.acquisition_channel = 'cabinet' ");
            } else if ("otros".equalsIgnoreCase(filter.getChannel())) {
                sql.append("WHERE (cth.acquisition_channel IS NULL OR cth.acquisition_channel != 'cabinet') ");
            }
        }
        
        sql.append("ORDER BY fd.hire_date DESC, fd.driver_id ");
        sql.append("LIMIT 5000");
        
        String finalSql = sql.toString();
        logger.debug("Ejecutando consulta optimizada desde tabla pre-calculada: {}", finalSql);
        
        try {
            jdbcTemplate.setQueryTimeout(90);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(finalSql, params.toArray());
            jdbcTemplate.setQueryTimeout(0);
            long executionTime = System.currentTimeMillis() - startTime;
            logger.info("Consulta desde tabla pre-calculada ejecutada exitosamente en {} ms. Filas retornadas: {}", executionTime, rows.size());
            
            if (executionTime > 5000) {
                logger.warn("ADVERTENCIA: La consulta tardó {} ms (más de 5 segundos).", executionTime);
            }
            
            boolean hasValidData = false;
            for (Map<String, Object> row : rows) {
                Object totalTrips = row.get("total_trips_historical");
                Object sumWorkTime = row.get("sum_work_time_seconds");
                boolean hasTrips = totalTrips != null && totalTrips instanceof Number && ((Number) totalTrips).intValue() > 0;
                boolean hasWorkTime = sumWorkTime != null && sumWorkTime instanceof Number && ((Number) sumWorkTime).longValue() > 0;
                if (hasTrips || hasWorkTime || row.get("total_trips_historical") != null || row.get("sum_work_time_seconds") != null) {
                    hasValidData = true;
                    break;
                }
            }
            
            if (!hasValidData && !rows.isEmpty()) {
                logger.info("Tabla pre-calculada no tiene datos válidos, calculando desde summary_daily en tiempo real...");
                return getOnboarding14dRealTimeFromSummaryDaily(filter);
            }
        
            List<DriverOnboardingDTO> drivers = new ArrayList<>();
            
            int countWithTrips = 0;
            int countWithWorkTime = 0;
            int countRegistered = 0;
        
            for (Map<String, Object> row : rows) {
                DriverOnboardingDTO driver = new DriverOnboardingDTO();
                driver.setDriverId((String) row.get("driver_id"));
                driver.setParkId((String) row.get("park_id"));
                driver.setChannel(null);
                driver.setFullName((String) row.get("full_name"));
                driver.setPhone((String) row.get("phone"));
                driver.setLicenseNumber((String) row.get("license_number"));
                
                Object hireDateObj = row.get("hire_date");
                if (hireDateObj instanceof java.sql.Date) {
                    driver.setStartDate(((java.sql.Date) hireDateObj).toLocalDate());
                } else if (hireDateObj instanceof java.sql.Timestamp) {
                    driver.setStartDate(((java.sql.Timestamp) hireDateObj).toLocalDateTime().toLocalDate());
                } else if (hireDateObj instanceof LocalDate) {
                    driver.setStartDate((LocalDate) hireDateObj);
                }
                
                driver.setStatus14d(null);
                
                Object totalTripsObj = row.get("total_trips_historical");
                if (totalTripsObj != null) {
                    if (totalTripsObj instanceof Number) {
                        driver.setTotalTrips14d(((Number) totalTripsObj).intValue());
                    } else {
                        driver.setTotalTrips14d(0);
                    }
                } else {
                    driver.setTotalTrips14d(0);
                }
                
                driver.setTotalOnlineTime14d(null);
                
                Object sumWorkTimeObj = row.get("sum_work_time_seconds");
                if (sumWorkTimeObj != null) {
                    if (sumWorkTimeObj instanceof Number) {
                        driver.setSumWorkTimeSeconds(((Number) sumWorkTimeObj).longValue());
                    } else {
                        driver.setSumWorkTimeSeconds(null);
                    }
                } else {
                    driver.setSumWorkTimeSeconds(null);
                }
                
                Object hasHistoricalConnectionObj = row.get("has_historical_connection");
                if (hasHistoricalConnectionObj != null) {
                    if (hasHistoricalConnectionObj instanceof Boolean) {
                        driver.setHasHistoricalConnection((Boolean) hasHistoricalConnectionObj);
                    } else if (hasHistoricalConnectionObj instanceof String) {
                        driver.setHasHistoricalConnection(Boolean.parseBoolean((String) hasHistoricalConnectionObj));
                    } else {
                        driver.setHasHistoricalConnection(false);
                    }
                } else {
                    driver.setHasHistoricalConnection(false);
                }
                
                Object leadCreatedAtObj = row.get("lead_created_at");
                if (leadCreatedAtObj != null) {
                    if (leadCreatedAtObj instanceof java.sql.Date) {
                        driver.setLeadCreatedAt(((java.sql.Date) leadCreatedAtObj).toLocalDate());
                    } else if (leadCreatedAtObj instanceof LocalDate) {
                        driver.setLeadCreatedAt((LocalDate) leadCreatedAtObj);
                    }
                }
                
                Object matchScoreObj = row.get("match_score");
                if (matchScoreObj != null) {
                    if (matchScoreObj instanceof Number) {
                        driver.setMatchScore(((Number) matchScoreObj).doubleValue());
                    }
                }
                
                Object isManualObj = row.get("is_manual");
                if (isManualObj != null) {
                    if (isManualObj instanceof Boolean) {
                        driver.setMatchManual((Boolean) isManualObj);
                    } else if (isManualObj instanceof String) {
                        driver.setMatchManual(Boolean.parseBoolean((String) isManualObj));
                    }
                }
                
                Object scoutIdObj = row.get("scout_id");
                if (scoutIdObj != null) {
                    driver.setScoutId((String) scoutIdObj);
                    driver.setHasScoutRegistration(true);
                } else {
                    driver.setHasScoutRegistration(false);
                }
                
                Object scoutNameObj = row.get("scout_name");
                if (scoutNameObj != null) {
                    driver.setScoutName((String) scoutNameObj);
                }
                
                Object scoutRegistrationDateObj = row.get("scout_registration_date");
                if (scoutRegistrationDateObj != null) {
                    if (scoutRegistrationDateObj instanceof java.sql.Date) {
                        driver.setScoutRegistrationDate(((java.sql.Date) scoutRegistrationDateObj).toLocalDate());
                    } else if (scoutRegistrationDateObj instanceof LocalDate) {
                        driver.setScoutRegistrationDate((LocalDate) scoutRegistrationDateObj);
                    }
                }
                
                Object scoutMatchScoreObj = row.get("scout_match_score");
                if (scoutMatchScoreObj != null) {
                    if (scoutMatchScoreObj instanceof Number) {
                        driver.setScoutMatchScore(((Number) scoutMatchScoreObj).doubleValue());
                    }
                }
                
                Object channelObj = row.get("acquisition_channel");
                if (channelObj != null) {
                    driver.setChannel((String) channelObj);
                }
                
                Object primeraConexionObj = row.get("primera_conexion_date");
                if (primeraConexionObj != null) {
                    driver.setPrimeraConexionDate(obtenerFecha(primeraConexionObj));
                }
                
                Object primerViajeObj = row.get("primer_viaje_date");
                if (primerViajeObj != null) {
                    driver.setPrimerViajeDate(obtenerFecha(primerViajeObj));
                }
                
                Object diasActivosObj = row.get("dias_activos");
                if (diasActivosObj != null) {
                    driver.setDiasActivos(obtenerInteger(diasActivosObj));
                }
                
                Object diasConectadosObj = row.get("dias_conectados");
                if (diasConectadosObj != null) {
                    driver.setDiasConectados(obtenerInteger(diasConectadosObj));
                }
                
                Object diasRegistroAConexionObj = row.get("dias_registro_a_conexion");
                if (diasRegistroAConexionObj != null) {
                    driver.setDiasRegistroAConexion(obtenerInteger(diasRegistroAConexionObj));
                }
                
                Object diasConexionAViajeObj = row.get("dias_conexion_a_viaje");
                if (diasConexionAViajeObj != null) {
                    driver.setDiasConexionAViaje(obtenerInteger(diasConexionAViajeObj));
                }
                
                Object diasPrimerViajeA25ViajesObj = row.get("dias_primer_viaje_a_25_viajes");
                if (diasPrimerViajeA25ViajesObj != null) {
                    driver.setDiasPrimerViajeA25Viajes(obtenerInteger(diasPrimerViajeA25ViajesObj));
                }
                
                Object tasaConversionObj = row.get("tasa_conversion_conexion");
                if (tasaConversionObj != null) {
                    driver.setTasaConversionConexion(obtenerDouble(tasaConversionObj));
                }
                
                Object tieneLeadObj = row.get("tiene_lead");
                if (tieneLeadObj != null) {
                    driver.setTieneLead(obtenerBoolean(tieneLeadObj));
                }
                
                Object tieneScoutObj = row.get("tiene_scout");
                if (tieneScoutObj != null) {
                    driver.setTieneScout(obtenerBoolean(tieneScoutObj));
                }
                
                Object matchScoreBajoObj = row.get("match_score_bajo");
                if (matchScoreBajoObj != null) {
                    driver.setMatchScoreBajo(obtenerBoolean(matchScoreBajoObj));
                }
                
                Object tieneInconsistenciasObj = row.get("tiene_inconsistencias");
                if (tieneInconsistenciasObj != null) {
                    driver.setTieneInconsistencias(obtenerBoolean(tieneInconsistenciasObj));
                }
                
                driver.setDays(new ArrayList<>());
                
                driver.setMilestones14d(null);
                driver.setMilestones7d(null);
                
                if (driver.getTotalTrips14d() != null && driver.getTotalTrips14d() > 0) {
                    countWithTrips++;
                }
                if (driver.getSumWorkTimeSeconds() != null && driver.getSumWorkTimeSeconds() > 0) {
                    countWithWorkTime++;
                }
                if ((driver.getSumWorkTimeSeconds() == null || driver.getSumWorkTimeSeconds() == 0) && 
                    (driver.getTotalTrips14d() == null || driver.getTotalTrips14d() == 0)) {
                    countRegistered++;
                }
                
                drivers.add(driver);
            }
            
            actualizarDriversConMilestones(drivers);
            
            countWithTrips = 0;
            countWithWorkTime = 0;
            countRegistered = 0;
            for (DriverOnboardingDTO driver : drivers) {
                if (driver.getTotalTrips14d() != null && driver.getTotalTrips14d() > 0) {
                    countWithTrips++;
                }
                if (driver.getSumWorkTimeSeconds() != null && driver.getSumWorkTimeSeconds() > 0) {
                    countWithWorkTime++;
                }
                if ((driver.getSumWorkTimeSeconds() == null || driver.getSumWorkTimeSeconds() == 0) && 
                    (driver.getTotalTrips14d() == null || driver.getTotalTrips14d() == 0)) {
                    countRegistered++;
                }
            }
            
            logger.info("Estadísticas desde tabla pre-calculada - Total: {}, Con viajes: {}, Con tiempo trabajo: {}, Registrados: {}", 
                drivers.size(), countWithTrips, countWithWorkTime, countRegistered);
        
            logger.debug("Iniciando carga de transacciones Yango para {} drivers", drivers.size());
            cargarTransaccionesYango14d(drivers);
            logger.debug("Carga de transacciones Yango completada");
            
            logger.debug("Calculando métricas de conversión para {} drivers", drivers.size());
            calcularMetricasConversion(drivers);
            logger.debug("Cálculo de métricas de conversión completado");
        
            return drivers;
        } catch (org.springframework.jdbc.CannotGetJdbcConnectionException e) {
            jdbcTemplate.setQueryTimeout(0);
            logger.error("Error de conexión a la base de datos. El servidor PostgreSQL puede estar saturado o no disponible.", e);
            logger.warn("Retornando lista vacía debido a problemas de conexión. Por favor, verifica el estado del servidor PostgreSQL.");
            return new ArrayList<>();
        } catch (org.springframework.dao.QueryTimeoutException e) {
            jdbcTemplate.setQueryTimeout(0);
            logger.error("Timeout al ejecutar consulta en tiempo real (90 segundos). Retornando lista vacía.", e);
            return new ArrayList<>();
        } catch (org.springframework.dao.DataAccessResourceFailureException e) {
            jdbcTemplate.setQueryTimeout(0);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "";
            Throwable cause = e.getCause();
            String causeMessage = cause != null && cause.getMessage() != null ? cause.getMessage() : "";
            
            if (errorMessage.contains("canceling statement due to user request") || 
                causeMessage.contains("canceling statement due to user request")) {
                logger.error("La consulta fue cancelada por timeout. La consulta tardó más de 90 segundos. Retornando lista vacía.", e);
                return new ArrayList<>();
            }
            
            if (errorMessage.contains("too many clients") || errorMessage.contains("53300") || errorMessage.contains("Connection is not available") ||
                errorMessage.contains("timeout") || errorMessage.contains("timed out") ||
                causeMessage.contains("too many clients") || causeMessage.contains("53300")) {
                logger.error("El servidor PostgreSQL está saturado o timeout. Retornando lista vacía.", e);
                return new ArrayList<>();
            }
            logger.error("Error de acceso a recursos de datos al ejecutar consulta en tiempo real", e);
            logger.error("SQL: {}", finalSql);
            logger.error("Parámetros: {}", params);
            return new ArrayList<>();
        } catch (Exception e) {
            jdbcTemplate.setQueryTimeout(0);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "";
            Throwable cause = e.getCause();
            String causeMessage = cause != null && cause.getMessage() != null ? cause.getMessage() : "";
            
            if (errorMessage.contains("canceling statement due to user request") || 
                causeMessage.contains("canceling statement due to user request")) {
                logger.error("La consulta fue cancelada por timeout. La consulta tardó más de 90 segundos. Retornando lista vacía.", e);
                return new ArrayList<>();
            }
            
            if (errorMessage.contains("too many clients") || errorMessage.contains("53300") || 
                errorMessage.contains("Connection is not available") ||
                causeMessage.contains("too many clients") || causeMessage.contains("53300")) {
                logger.error("Error de conexión a la base de datos (servidor saturado). Retornando lista vacía.", e);
                return new ArrayList<>();
            }
            logger.error("Error al ejecutar consulta SQL en tiempo real", e);
            logger.error("SQL: {}", finalSql);
            logger.error("Parámetros: {}", params);
            return new ArrayList<>();
        }
    }
    
    @Transactional(readOnly = true)
    private List<DriverOnboardingDTO> getOnboarding14dRealTimeFromSummaryDaily(OnboardingFilterDTO filter) {
        String parkId = filter.getParkId() != null && !filter.getParkId().isEmpty() 
            ? filter.getParkId() 
            : DEFAULT_PARK_ID;
        
        long startTime = System.currentTimeMillis();
        logger.info("Calculando datos históricos completos desde summary_daily en tiempo real para parkId: {}", parkId);
        
        StringBuilder sql = new StringBuilder();
        sql.append("WITH filtered_drivers AS ( ");
        sql.append("  SELECT driver_id, park_id, hire_date, full_name, phone, license_number ");
        sql.append("  FROM drivers ");
        sql.append("  WHERE park_id = ? ");
        List<Object> params = new ArrayList<>();
        params.add(parkId);
        if (filter.getStartDateFrom() != null) {
            sql.append("    AND hire_date >= ? ");
            params.add(filter.getStartDateFrom());
        }
        if (filter.getStartDateTo() != null) {
            sql.append("    AND hire_date <= ? ");
            params.add(filter.getStartDateTo());
        }
        sql.append("), latest_tracking AS ( ");
        sql.append("  SELECT DISTINCT ON (cth.driver_id) ");
        sql.append("    cth.driver_id, ");
        sql.append("    cth.acquisition_channel ");
        sql.append("  FROM contractor_tracking_history cth ");
        sql.append("  INNER JOIN filtered_drivers fd ON cth.driver_id = fd.driver_id ");
        sql.append("  ORDER BY cth.driver_id, cth.calculation_date DESC ");
        sql.append("), driver_trips_time AS ( ");
        sql.append("  SELECT ");
        sql.append("    fd.driver_id, ");
        sql.append("    COALESCE(SUM(sd.count_orders_completed), 0) as total_trips_historical, ");
        sql.append("    SUM(sd.sum_work_time_seconds) as sum_work_time_seconds, ");
        sql.append("    CASE WHEN COUNT(CASE WHEN COALESCE(sd.sum_work_time_seconds, 0) > 0 THEN 1 END) > 0 THEN true ELSE false END as has_historical_connection ");
        sql.append("  FROM filtered_drivers fd ");
        sql.append("  LEFT JOIN summary_daily sd ON sd.driver_id = fd.driver_id ");
        sql.append("    AND to_date_immutable(sd.date_file) >= fd.hire_date ");
        sql.append("    AND to_date_immutable(sd.date_file) < fd.hire_date + INTERVAL '14 days' ");
        sql.append("  GROUP BY fd.driver_id ");
        sql.append("), latest_lead_match AS ( ");
        sql.append("  SELECT DISTINCT ON (lm.driver_id) ");
        sql.append("    lm.driver_id, ");
        sql.append("    lm.lead_created_at, ");
        sql.append("    lm.match_score, ");
        sql.append("    lm.is_manual ");
        sql.append("  FROM lead_matches lm ");
        sql.append("  INNER JOIN filtered_drivers fd ON lm.driver_id = fd.driver_id ");
        sql.append("  WHERE lm.is_discarded = false ");
        sql.append("  ORDER BY lm.driver_id, lm.matched_at DESC ");
        sql.append("), latest_scout_registration AS ( ");
        sql.append("  SELECT DISTINCT ON (sr.driver_id) ");
        sql.append("    sr.driver_id, ");
        sql.append("    sr.scout_id, ");
        sql.append("    sr.registration_date as scout_registration_date, ");
        sql.append("    sr.match_score as scout_match_score, ");
        sql.append("    s.scout_name ");
        sql.append("  FROM scout_registrations sr ");
        sql.append("  INNER JOIN filtered_drivers fd ON sr.driver_id = fd.driver_id ");
        sql.append("  LEFT JOIN scouts s ON sr.scout_id = s.scout_id ");
        sql.append("  WHERE sr.driver_id IS NOT NULL ");
        sql.append("    AND sr.is_matched = true ");
        sql.append("  ORDER BY sr.driver_id, sr.registration_date DESC ");
        sql.append(") ");
        sql.append("SELECT ");
        sql.append("  fd.driver_id, ");
        sql.append("  fd.park_id, ");
        sql.append("  fd.full_name, ");
        sql.append("  fd.phone, ");
        sql.append("  fd.hire_date, ");
        sql.append("  fd.license_number, ");
        sql.append("  dtt.sum_work_time_seconds, ");
        sql.append("  COALESCE(dtt.total_trips_historical, 0) as total_trips_historical, ");
        sql.append("  COALESCE(dtt.has_historical_connection, false) as has_historical_connection, ");
        sql.append("  cth.acquisition_channel, ");
        sql.append("  lm.lead_created_at, ");
        sql.append("  lm.match_score, ");
        sql.append("  lm.is_manual, ");
        sql.append("  sr.scout_id, ");
        sql.append("  sr.scout_name, ");
        sql.append("  sr.scout_registration_date, ");
        sql.append("  sr.scout_match_score ");
        sql.append("FROM filtered_drivers fd ");
        sql.append("LEFT JOIN driver_trips_time dtt ON dtt.driver_id = fd.driver_id ");
        sql.append("LEFT JOIN latest_tracking cth ON cth.driver_id = fd.driver_id ");
        sql.append("LEFT JOIN latest_lead_match lm ON lm.driver_id = fd.driver_id ");
        sql.append("LEFT JOIN latest_scout_registration sr ON sr.driver_id = fd.driver_id ");
        
        if (filter.getChannel() != null && !filter.getChannel().isEmpty()) {
            if ("cabinet".equalsIgnoreCase(filter.getChannel())) {
                sql.append("WHERE cth.acquisition_channel = 'cabinet' ");
            } else if ("otros".equalsIgnoreCase(filter.getChannel())) {
                sql.append("WHERE (cth.acquisition_channel IS NULL OR cth.acquisition_channel != 'cabinet') ");
            }
        }
        
        sql.append("ORDER BY fd.hire_date DESC, fd.driver_id ");
        
        int page = filter.getPage() != null ? filter.getPage() : 0;
        int size = filter.getSize() != null ? filter.getSize() : 50;
        int offset = page * size;
        
        sql.append(" LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);
        
        String finalSql = sql.toString();
        logger.debug("Ejecutando SQL optimizado calculando desde summary_daily en tiempo real: {}", finalSql);
        
        try {
            jdbcTemplate.setQueryTimeout(45);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(finalSql, params.toArray());
            jdbcTemplate.setQueryTimeout(0);
            long executionTime = System.currentTimeMillis() - startTime;
            logger.info("Consulta desde summary_daily ejecutada exitosamente en {} ms. Filas retornadas: {}", executionTime, rows.size());
            
            if (executionTime > 10000) {
                logger.warn("ADVERTENCIA: La consulta tardó {} ms (más de 10 segundos). Considerar optimización adicional.", executionTime);
            }
        
            List<DriverOnboardingDTO> drivers = new ArrayList<>();
            
            int countWithTrips = 0;
            int countWithWorkTime = 0;
            int countRegistered = 0;
        
            for (Map<String, Object> row : rows) {
                DriverOnboardingDTO driver = new DriverOnboardingDTO();
                driver.setDriverId((String) row.get("driver_id"));
                driver.setParkId((String) row.get("park_id"));
                driver.setChannel((String) row.get("acquisition_channel"));
                driver.setFullName((String) row.get("full_name"));
                driver.setPhone((String) row.get("phone"));
                driver.setLicenseNumber((String) row.get("license_number"));
                
                Object hireDateObj = row.get("hire_date");
                if (hireDateObj instanceof java.sql.Date) {
                    driver.setStartDate(((java.sql.Date) hireDateObj).toLocalDate());
                } else if (hireDateObj instanceof java.sql.Timestamp) {
                    driver.setStartDate(((java.sql.Timestamp) hireDateObj).toLocalDateTime().toLocalDate());
                } else if (hireDateObj instanceof LocalDate) {
                    driver.setStartDate((LocalDate) hireDateObj);
                }
                
                driver.setStatus14d(null);
                
                Object totalTripsObj = row.get("total_trips_historical");
                if (totalTripsObj != null) {
                    if (totalTripsObj instanceof Number) {
                        driver.setTotalTrips14d(((Number) totalTripsObj).intValue());
                    } else {
                        driver.setTotalTrips14d(0);
                    }
                } else {
                    driver.setTotalTrips14d(0);
                }
                
                driver.setTotalOnlineTime14d(null);
                
                Object sumWorkTimeObj = row.get("sum_work_time_seconds");
                if (sumWorkTimeObj != null) {
                    if (sumWorkTimeObj instanceof Number) {
                        driver.setSumWorkTimeSeconds(((Number) sumWorkTimeObj).longValue());
                    } else {
                        driver.setSumWorkTimeSeconds(null);
                    }
                } else {
                    driver.setSumWorkTimeSeconds(null);
                }
                
                Object hasHistoricalConnectionObj = row.get("has_historical_connection");
                if (hasHistoricalConnectionObj != null) {
                    if (hasHistoricalConnectionObj instanceof Boolean) {
                        driver.setHasHistoricalConnection((Boolean) hasHistoricalConnectionObj);
                    } else if (hasHistoricalConnectionObj instanceof String) {
                        driver.setHasHistoricalConnection(Boolean.parseBoolean((String) hasHistoricalConnectionObj));
                    } else {
                        driver.setHasHistoricalConnection(false);
                    }
                } else {
                    driver.setHasHistoricalConnection(false);
                }
                
                Object leadCreatedAtObj = row.get("lead_created_at");
                if (leadCreatedAtObj != null) {
                    if (leadCreatedAtObj instanceof java.sql.Date) {
                        driver.setLeadCreatedAt(((java.sql.Date) leadCreatedAtObj).toLocalDate());
                    } else if (leadCreatedAtObj instanceof LocalDate) {
                        driver.setLeadCreatedAt((LocalDate) leadCreatedAtObj);
                    }
                }
                
                Object matchScoreObj = row.get("match_score");
                if (matchScoreObj != null) {
                    if (matchScoreObj instanceof Number) {
                        driver.setMatchScore(((Number) matchScoreObj).doubleValue());
                    }
                }
                
                Object isManualObj = row.get("is_manual");
                if (isManualObj != null) {
                    if (isManualObj instanceof Boolean) {
                        driver.setMatchManual((Boolean) isManualObj);
                    } else if (isManualObj instanceof String) {
                        driver.setMatchManual(Boolean.parseBoolean((String) isManualObj));
                    }
                }
                
                Object scoutIdObj = row.get("scout_id");
                if (scoutIdObj != null) {
                    driver.setScoutId((String) scoutIdObj);
                    driver.setHasScoutRegistration(true);
                } else {
                    driver.setHasScoutRegistration(false);
                }
                
                Object scoutNameObj = row.get("scout_name");
                if (scoutNameObj != null) {
                    driver.setScoutName((String) scoutNameObj);
                }
                
                Object scoutRegistrationDateObj = row.get("scout_registration_date");
                if (scoutRegistrationDateObj != null) {
                    if (scoutRegistrationDateObj instanceof java.sql.Date) {
                        driver.setScoutRegistrationDate(((java.sql.Date) scoutRegistrationDateObj).toLocalDate());
                    } else if (scoutRegistrationDateObj instanceof LocalDate) {
                        driver.setScoutRegistrationDate((LocalDate) scoutRegistrationDateObj);
                    }
                }
                
                Object scoutMatchScoreObj = row.get("scout_match_score");
                if (scoutMatchScoreObj != null) {
                    if (scoutMatchScoreObj instanceof Number) {
                        driver.setScoutMatchScore(((Number) scoutMatchScoreObj).doubleValue());
                    }
                }
                
                driver.setDays(new ArrayList<>());
                
                driver.setMilestones14d(null);
                driver.setMilestones7d(null);
                
                if (driver.getTotalTrips14d() != null && driver.getTotalTrips14d() > 0) {
                    countWithTrips++;
                }
                if (driver.getSumWorkTimeSeconds() != null && driver.getSumWorkTimeSeconds() > 0) {
                    countWithWorkTime++;
                }
                if ((driver.getSumWorkTimeSeconds() == null || driver.getSumWorkTimeSeconds() == 0) && 
                    (driver.getTotalTrips14d() == null || driver.getTotalTrips14d() == 0)) {
                    countRegistered++;
                }
                
                drivers.add(driver);
            }
            
            actualizarDriversConMilestones(drivers);
            
            countWithTrips = 0;
            countWithWorkTime = 0;
            countRegistered = 0;
            for (DriverOnboardingDTO driver : drivers) {
                if (driver.getTotalTrips14d() != null && driver.getTotalTrips14d() > 0) {
                    countWithTrips++;
                }
                if (driver.getSumWorkTimeSeconds() != null && driver.getSumWorkTimeSeconds() > 0) {
                    countWithWorkTime++;
                }
                if ((driver.getSumWorkTimeSeconds() == null || driver.getSumWorkTimeSeconds() == 0) && 
                    (driver.getTotalTrips14d() == null || driver.getTotalTrips14d() == 0)) {
                    countRegistered++;
                }
            }
            
            logger.info("Estadísticas de cálculo desde summary_daily en tiempo real - Total: {}, Con viajes: {}, Con tiempo trabajo: {}, Registrados: {}", 
                drivers.size(), countWithTrips, countWithWorkTime, countRegistered);
        
            cargarTransaccionesYango14d(drivers);
            calcularMetricasConversion(drivers);
        
            return drivers;
        } catch (org.springframework.dao.QueryTimeoutException e) {
            jdbcTemplate.setQueryTimeout(0);
            logger.error("Timeout al ejecutar consulta desde summary_daily (45 segundos). Retornando lista vacía.", e);
            return new ArrayList<>();
        } catch (Exception e) {
            jdbcTemplate.setQueryTimeout(0);
            logger.error("Error al ejecutar consulta SQL desde summary_daily en tiempo real", e);
            logger.error("SQL: {}", finalSql);
            logger.error("Parámetros: {}", params);
            return new ArrayList<>();
        }
    }
    
    public List<DriverOnboardingDTO> getDriversByIds(List<String> driverIds, String parkId) {
        if (driverIds == null || driverIds.isEmpty()) {
            return new ArrayList<>();
        }
        
        parkId = parkId != null && !parkId.isEmpty() ? parkId : DEFAULT_PARK_ID;
        
        logger.info("Obteniendo {} drivers por IDs para parkId: {}", driverIds.size(), parkId);
        
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ");
        sql.append("  d.driver_id, ");
        sql.append("  d.park_id, ");
        sql.append("  d.full_name, ");
        sql.append("  d.phone, ");
        sql.append("  d.hire_date, ");
        sql.append("  d.license_number, ");
        sql.append("  COALESCE(SUM(sd.count_orders_completed), 0) as total_trips_14d, ");
        sql.append("  COALESCE(SUM(sd.sum_work_time_seconds), NULL) as sum_work_time_seconds, ");
        sql.append("  CASE WHEN EXISTS ( ");
        sql.append("    SELECT 1 FROM summary_daily sd_hist ");
        sql.append("    WHERE sd_hist.driver_id = d.driver_id ");
        sql.append("      AND COALESCE(sd_hist.sum_work_time_seconds, 0) > 0 ");
        sql.append("  ) THEN true ELSE false END as has_historical_connection, ");
        sql.append("  (SELECT cth.acquisition_channel FROM contractor_tracking_history cth ");
        sql.append("   WHERE cth.driver_id = d.driver_id ORDER BY cth.calculation_date DESC LIMIT 1) as acquisition_channel, ");
        sql.append("  lm.lead_created_at, ");
        sql.append("  lm.match_score, ");
        sql.append("  lm.is_manual, ");
        sql.append("  sr.scout_id, ");
        sql.append("  sr.scout_name, ");
        sql.append("  sr.scout_registration_date, ");
        sql.append("  sr.scout_match_score ");
        sql.append("FROM drivers d ");
        sql.append("LEFT JOIN summary_daily sd ON d.driver_id = sd.driver_id ");
        sql.append("LEFT JOIN ( ");
        sql.append("  SELECT DISTINCT ON (driver_id) driver_id, lead_created_at, match_score, is_manual ");
        sql.append("  FROM lead_matches ");
        sql.append("  WHERE COALESCE(is_discarded, false) = false ");
        sql.append("  ORDER BY driver_id, matched_at DESC ");
        sql.append(") lm ON lm.driver_id = d.driver_id ");
        sql.append("LEFT JOIN ( ");
        sql.append("  SELECT DISTINCT ON (sr.driver_id) ");
        sql.append("    sr.driver_id, ");
        sql.append("    sr.scout_id, ");
        sql.append("    sr.registration_date as scout_registration_date, ");
        sql.append("    sr.match_score as scout_match_score, ");
        sql.append("    s.scout_name ");
        sql.append("  FROM scout_registrations sr ");
        sql.append("  LEFT JOIN scouts s ON sr.scout_id = s.scout_id ");
        sql.append("  WHERE sr.driver_id IS NOT NULL ");
        sql.append("    AND sr.is_matched = true ");
        sql.append("  ORDER BY sr.driver_id, sr.registration_date DESC ");
        sql.append(") sr ON sr.driver_id = d.driver_id ");
        sql.append("WHERE d.park_id = ? ");
        sql.append("  AND d.driver_id = ANY(?) ");
        sql.append("GROUP BY d.driver_id, d.park_id, d.full_name, d.phone, d.hire_date, d.license_number, lm.lead_created_at, lm.match_score, lm.is_manual, sr.scout_id, sr.scout_name, sr.scout_registration_date, sr.scout_match_score ");
        sql.append("ORDER BY d.hire_date DESC, d.driver_id");
        
        try {
            String placeholders = driverIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
            String sqlWithPlaceholders = sql.toString().replace("d.driver_id = ANY(?)", "d.driver_id IN (" + placeholders + ")");
            
            List<Object> params = new ArrayList<>();
            params.add(parkId);
            params.addAll(driverIds);
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sqlWithPlaceholders, params.toArray());
            logger.info("Se obtuvieron {} drivers por IDs", rows.size());
            
            List<DriverOnboardingDTO> drivers = new ArrayList<>();
            
            for (Map<String, Object> row : rows) {
                DriverOnboardingDTO driver = new DriverOnboardingDTO();
                driver.setDriverId((String) row.get("driver_id"));
                driver.setParkId((String) row.get("park_id"));
                driver.setChannel(null);
                driver.setFullName((String) row.get("full_name"));
                driver.setPhone((String) row.get("phone"));
                driver.setLicenseNumber((String) row.get("license_number"));
                
                Object hireDateObj = row.get("hire_date");
                if (hireDateObj instanceof java.sql.Date) {
                    driver.setStartDate(((java.sql.Date) hireDateObj).toLocalDate());
                } else if (hireDateObj instanceof java.sql.Timestamp) {
                    driver.setStartDate(((java.sql.Timestamp) hireDateObj).toLocalDateTime().toLocalDate());
                } else if (hireDateObj instanceof LocalDate) {
                    driver.setStartDate((LocalDate) hireDateObj);
                }
                
                Object totalTripsObj = row.get("total_trips_14d");
                if (totalTripsObj != null && totalTripsObj instanceof Number) {
                    driver.setTotalTrips14d(((Number) totalTripsObj).intValue());
                } else {
                    driver.setTotalTrips14d(0);
                }
                
                Object sumWorkTimeObj = row.get("sum_work_time_seconds");
                if (sumWorkTimeObj != null && sumWorkTimeObj instanceof Number) {
                    driver.setSumWorkTimeSeconds(((Number) sumWorkTimeObj).longValue());
                }
                
                Object hasHistoricalConnectionObj = row.get("has_historical_connection");
                if (hasHistoricalConnectionObj instanceof Boolean) {
                    driver.setHasHistoricalConnection((Boolean) hasHistoricalConnectionObj);
                } else {
                    driver.setHasHistoricalConnection(false);
                }
                
                Object leadCreatedAtObj = row.get("lead_created_at");
                if (leadCreatedAtObj instanceof java.sql.Date) {
                    driver.setLeadCreatedAt(((java.sql.Date) leadCreatedAtObj).toLocalDate());
                } else if (leadCreatedAtObj instanceof LocalDate) {
                    driver.setLeadCreatedAt((LocalDate) leadCreatedAtObj);
                }
                
                Object matchScoreObj = row.get("match_score");
                if (matchScoreObj != null && matchScoreObj instanceof Number) {
                    driver.setMatchScore(((Number) matchScoreObj).doubleValue());
                }
                
                Object isManualObj = row.get("is_manual");
                if (isManualObj != null) {
                    if (isManualObj instanceof Boolean) {
                        driver.setMatchManual((Boolean) isManualObj);
                    } else if (isManualObj instanceof String) {
                        driver.setMatchManual(Boolean.parseBoolean((String) isManualObj));
                    }
                }
                
                driver.setTotalOnlineTime14d(null);
                driver.setDays(new ArrayList<>());
                driver.setMilestones14d(null);
                driver.setMilestones7d(null);
                
                calcularStatus14d(driver);
                
                drivers.add(driver);
            }
            
            actualizarDriversConMilestones(drivers);
            
            cargarTransaccionesYango14d(drivers);
            calcularMetricasConversion(drivers);
            
            return drivers;
        } catch (Exception e) {
            logger.error("Error al obtener drivers por IDs", e);
            return new ArrayList<>();
        }
    }
    
    private void calcularStatus14d(DriverOnboardingDTO driver) {
        int totalTrips = driver.getTotalTrips14d() != null ? driver.getTotalTrips14d() : 0;
        boolean hasConnection = (driver.getSumWorkTimeSeconds() != null && driver.getSumWorkTimeSeconds() > 0) 
            || (driver.getHasHistoricalConnection() != null && driver.getHasHistoricalConnection());
        
        if (totalTrips > 0) {
            driver.setStatus14d("activo_con_viajes");
        } else if (hasConnection) {
            driver.setStatus14d("conecto_sin_viajes");
        } else {
            driver.setStatus14d("solo_registro");
        }
    }
    
    private void actualizarDriversConMilestones(List<DriverOnboardingDTO> drivers) {
        if (drivers == null || drivers.isEmpty()) {
            return;
        }
        
        List<String> driverIdsSinViajes = new ArrayList<>();
        for (DriverOnboardingDTO driver : drivers) {
            if ((driver.getTotalTrips14d() == null || driver.getTotalTrips14d() == 0) && 
                (driver.getSumWorkTimeSeconds() == null || driver.getSumWorkTimeSeconds() == 0)) {
                driverIdsSinViajes.add(driver.getDriverId());
            }
        }
        
        if (driverIdsSinViajes.isEmpty()) {
            logger.debug("No hay drivers sin datos históricos para actualizar con milestones");
            return;
        }
        
        logger.info("Cargando milestones para {} drivers sin datos históricos", driverIdsSinViajes.size());
        try {
            Set<String> driverIdsSet = new HashSet<>(driverIdsSinViajes);
            if (driverIdsSet.size() > 100) {
                logger.warn("Muchos drivers sin datos ({}) - esto podría ser lento", driverIdsSet.size());
            }
            List<MilestoneInstance> milestones = milestoneInstanceRepository.findByDriverIdIn(driverIdsSet);
            logger.info("Encontrados {} milestones para {} drivers únicos", milestones.size(), driverIdsSet.size());
            
            if (milestones.isEmpty()) {
                logger.warn("No se encontraron milestones para ningún driver. Esto podría indicar que los milestones no están calculados o hay un problema con los driverIds.");
                return;
            }
            
            Map<String, List<MilestoneInstance>> milestonesByDriver = new HashMap<>();
            for (MilestoneInstance milestone : milestones) {
                milestonesByDriver.computeIfAbsent(milestone.getDriverId(), k -> new ArrayList<>()).add(milestone);
            }
            
            int actualizados = 0;
            int conViajes = 0;
            int conConexion = 0;
            for (DriverOnboardingDTO driver : drivers) {
                List<MilestoneInstance> driverMilestones = milestonesByDriver.get(driver.getDriverId());
                if (driverMilestones != null && !driverMilestones.isEmpty()) {
                    MilestoneInstance milestone14d = driverMilestones.stream()
                        .filter(m -> m.getPeriodDays() == 14 && (m.getMilestoneType() == 1 || m.getMilestoneType() == 5 || m.getMilestoneType() == 25))
                        .findFirst()
                        .orElse(null);
                    
                    if (milestone14d == null) {
                        milestone14d = driverMilestones.stream()
                            .filter(m -> m.getPeriodDays() == 7 && (m.getMilestoneType() == 1 || m.getMilestoneType() == 5 || m.getMilestoneType() == 25))
                            .findFirst()
                            .orElse(null);
                    }
                    
                    if (milestone14d != null) {
                        boolean actualizado = false;
                        if (driver.getTotalTrips14d() == null || driver.getTotalTrips14d() == 0) {
                            int tripCount = milestone14d.getTripCount() != null && milestone14d.getTripCount() > 0 
                                ? milestone14d.getTripCount() 
                                : milestone14d.getMilestoneType();
                            driver.setTotalTrips14d(Math.max(tripCount, milestone14d.getMilestoneType()));
                            actualizado = true;
                            conViajes++;
                            logger.debug("Actualizando totalTrips14d para driver {} desde milestone tipo {} (periodDays: {}): {}", 
                                driver.getDriverId(), milestone14d.getMilestoneType(), milestone14d.getPeriodDays(), 
                                driver.getTotalTrips14d());
                        }
                        
                        if (driver.getSumWorkTimeSeconds() == null || driver.getSumWorkTimeSeconds() == 0) {
                            driver.setSumWorkTimeSeconds(1L);
                            actualizado = true;
                            conConexion++;
                        }
                        
                        if (driver.getHasHistoricalConnection() == null || !driver.getHasHistoricalConnection()) {
                            driver.setHasHistoricalConnection(true);
                            actualizado = true;
                        }
                        
                        if (actualizado) {
                            calcularStatus14d(driver);
                            actualizados++;
                        }
                    }
                }
            }
            
            for (DriverOnboardingDTO driver : drivers) {
                if (driver.getStatus14d() == null) {
                    calcularStatus14d(driver);
                }
            }
            
            if (actualizados > 0) {
                logger.info("Actualizados {} drivers con datos de milestones ({} con viajes, {} con conexión)", 
                    actualizados, conViajes, conConexion);
            } else {
                logger.warn("No se actualizó ningún driver con milestones. Milestones encontrados: {}", milestones.size());
            }
        } catch (Exception e) {
            logger.error("Error al cargar milestones para actualizar datos históricos", e);
        }
    }
    
    private void cargarTransaccionesYango14d(List<DriverOnboardingDTO> drivers) {
        if (drivers == null || drivers.isEmpty()) {
            return;
        }
        
        try {
            List<String> driverIds = drivers.stream()
                .map(DriverOnboardingDTO::getDriverId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toList());
            
            if (driverIds.isEmpty()) {
                return;
            }
            
            Map<String, LocalDate> driverStartDates = drivers.stream()
                .filter(d -> d.getStartDate() != null)
                .collect(java.util.stream.Collectors.toMap(
                    DriverOnboardingDTO::getDriverId,
                    DriverOnboardingDTO::getStartDate
                ));
            
            if (driverStartDates.isEmpty()) {
                return;
            }
            
            LocalDate minStartDate = driverStartDates.values().stream()
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());
            LocalDate maxEndDate = driverStartDates.values().stream()
                .map(date -> date.plusDays(14))
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());
            
            Map<String, List<com.yego.contractortracker.dto.YangoTransactionMatchedDTO>> transaccionesPorDriver = 
                yangoTransactionService.obtenerTransaccionesMatcheadasPorDriver(
                    driverIds, minStartDate, maxEndDate
                );
            
            for (DriverOnboardingDTO driver : drivers) {
                if (driver.getStartDate() == null) {
                    driver.setYangoTransactions14d(new ArrayList<>());
                    continue;
                }
                
                LocalDate fechaDesde = driver.getStartDate();
                LocalDate fechaHasta = driver.getStartDate().plusDays(14);
                
                List<com.yego.contractortracker.dto.YangoTransactionMatchedDTO> transaccionesDriver = 
                    transaccionesPorDriver.getOrDefault(driver.getDriverId(), new ArrayList<>());
                
                List<com.yego.contractortracker.dto.YangoTransactionMatchedDTO> transacciones14d = 
                    transaccionesDriver.stream()
                        .filter(t -> {
                            if (t.getTransactionDate() == null) return false;
                            LocalDate transactionDate = t.getTransactionDate().toLocalDate();
                            return !transactionDate.isBefore(fechaDesde) && !transactionDate.isAfter(fechaHasta);
                        })
                        .collect(java.util.stream.Collectors.toList());
                
                driver.setYangoTransactions14d(transacciones14d);
            }
            
            logger.debug("Transacciones Yango cargadas para {} drivers", 
                drivers.stream().filter(d -> d.getYangoTransactions14d() != null && !d.getYangoTransactions14d().isEmpty()).count());
            
        } catch (org.springframework.jdbc.CannotGetJdbcConnectionException e) {
            logger.error("No se pudo obtener conexión JDBC al cargar transacciones Yango. El servidor PostgreSQL puede estar saturado o no disponible.", e);
            for (DriverOnboardingDTO driver : drivers) {
                if (driver.getYangoTransactions14d() == null) {
                    driver.setYangoTransactions14d(new ArrayList<>());
                }
            }
        } catch (org.springframework.dao.DataAccessResourceFailureException e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "";
            if (errorMessage.contains("Connection reset") || errorMessage.contains("I/O error") || 
                errorMessage.contains("Unable to commit")) {
                logger.error("Error de conexión a la base de datos al cargar transacciones Yango. La conexión se reseteó o el servidor está saturado.", e);
            } else {
                logger.error("Error de acceso a recursos de datos al cargar transacciones Yango", e);
            }
            for (DriverOnboardingDTO driver : drivers) {
                if (driver.getYangoTransactions14d() == null) {
                    driver.setYangoTransactions14d(new ArrayList<>());
                }
            }
        } catch (Exception e) {
            String errorMessage = e.getMessage() != null ? e.getMessage() : "";
            if (errorMessage.contains("Connection reset") || errorMessage.contains("I/O error") || 
                errorMessage.contains("Unable to commit")) {
                logger.error("Error de conexión al cargar transacciones Yango para drivers. La conexión se reseteó durante la consulta.", e);
            } else {
                logger.error("Error inesperado al cargar transacciones Yango para drivers", e);
            }
            for (DriverOnboardingDTO driver : drivers) {
                if (driver.getYangoTransactions14d() == null) {
                    driver.setYangoTransactions14d(new ArrayList<>());
                }
            }
        }
    }
    
    private void calcularMetricasConversion(List<DriverOnboardingDTO> drivers) {
        if (drivers == null || drivers.isEmpty()) {
            return;
        }
        
        try {
            List<String> driverIds = drivers.stream()
                .map(DriverOnboardingDTO::getDriverId)
                .filter(id -> id != null)
                .collect(java.util.stream.Collectors.toList());
            
            if (driverIds.isEmpty()) {
                return;
            }
            
            String placeholders = driverIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
            
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ");
            sql.append("  sd.driver_id, ");
            sql.append("  MIN(CASE WHEN COALESCE(sd.sum_work_time_seconds, 0) > 0 THEN TO_DATE(sd.date_file, 'DD-MM-YYYY') END) as primera_conexion_date, ");
            sql.append("  MIN(CASE WHEN COALESCE(sd.count_orders_completed, 0) > 0 THEN TO_DATE(sd.date_file, 'DD-MM-YYYY') END) as primer_viaje_date, ");
            sql.append("  COUNT(DISTINCT CASE WHEN COALESCE(sd.count_orders_completed, 0) > 0 THEN TO_DATE(sd.date_file, 'DD-MM-YYYY') END) as dias_activos, ");
            sql.append("  COUNT(DISTINCT CASE WHEN COALESCE(sd.sum_work_time_seconds, 0) > 0 THEN TO_DATE(sd.date_file, 'DD-MM-YYYY') END) as dias_conectados ");
            sql.append("FROM summary_daily sd ");
            sql.append("INNER JOIN drivers d ON sd.driver_id = d.driver_id ");
            sql.append("WHERE sd.driver_id IN (").append(placeholders).append(") ");
            sql.append("  AND TO_DATE(sd.date_file, 'DD-MM-YYYY') <= d.hire_date + INTERVAL '14 days' ");
            sql.append("GROUP BY sd.driver_id");
            
            Map<String, Map<String, Object>> fechasPorDriver = new HashMap<>();
            List<Object> params = new ArrayList<>(driverIds);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            
            for (Map<String, Object> row : rows) {
                String driverId = (String) row.get("driver_id");
                Map<String, Object> fechas = new HashMap<>();
                fechas.put("primeraConexion", row.get("primera_conexion_date"));
                fechas.put("primerViaje", row.get("primer_viaje_date"));
                fechas.put("diasActivos", row.get("dias_activos"));
                fechas.put("diasConectados", row.get("dias_conectados"));
                fechasPorDriver.put(driverId, fechas);
            }
            
            // Consulta adicional para verificar si la primera conexión tuvo viajes ese mismo día
            if (!driverIds.isEmpty()) {
                StringBuilder sqlPrimeraConexion = new StringBuilder();
                sqlPrimeraConexion.append("SELECT ");
                sqlPrimeraConexion.append("  primera.driver_id, ");
                sqlPrimeraConexion.append("  CASE WHEN COALESCE(sd.count_orders_completed, 0) > 0 THEN 1 ELSE 0 END as tiene_viajes ");
                sqlPrimeraConexion.append("FROM ( ");
                sqlPrimeraConexion.append("  SELECT sd2.driver_id, MIN(CASE WHEN COALESCE(sd2.sum_work_time_seconds, 0) > 0 THEN TO_DATE(sd2.date_file, 'DD-MM-YYYY') END) as primera_conexion ");
                sqlPrimeraConexion.append("  FROM summary_daily sd2 ");
                sqlPrimeraConexion.append("  INNER JOIN drivers d2 ON sd2.driver_id = d2.driver_id ");
                sqlPrimeraConexion.append("  WHERE sd2.driver_id IN (").append(placeholders).append(") ");
                sqlPrimeraConexion.append("    AND TO_DATE(sd2.date_file, 'DD-MM-YYYY') <= d2.hire_date + INTERVAL '14 days' ");
                sqlPrimeraConexion.append("  GROUP BY sd2.driver_id ");
                sqlPrimeraConexion.append(") primera ");
                sqlPrimeraConexion.append("LEFT JOIN summary_daily sd ON sd.driver_id = primera.driver_id AND TO_DATE(sd.date_file, 'DD-MM-YYYY') = primera.primera_conexion ");
                sqlPrimeraConexion.append("WHERE primera.driver_id IN (").append(placeholders).append(")");
                
                List<Object> paramsPrimeraConexion = new ArrayList<>(driverIds);
                paramsPrimeraConexion.addAll(driverIds);
                List<Map<String, Object>> rowsPrimeraConexion = jdbcTemplate.queryForList(sqlPrimeraConexion.toString(), paramsPrimeraConexion.toArray());
                for (Map<String, Object> row : rowsPrimeraConexion) {
                    String driverId = (String) row.get("driver_id");
                    Map<String, Object> fechas = fechasPorDriver.get(driverId);
                    if (fechas != null) {
                        Object tieneViajesObj = row.get("tiene_viajes");
                        if (tieneViajesObj != null && tieneViajesObj instanceof Number) {
                            fechas.put("primeraConexionTieneViajes", ((Number) tieneViajesObj).intValue() > 0);
                        } else {
                            fechas.put("primeraConexionTieneViajes", false);
                        }
                    }
                }
            }
            
            for (DriverOnboardingDTO driver : drivers) {
                if (driver.getStartDate() == null) {
                    continue;
                }
                
                Map<String, Object> fechas = fechasPorDriver.get(driver.getDriverId());
                if (fechas != null) {
                    Object primeraConexionObj = fechas.get("primeraConexion");
                    if (primeraConexionObj != null) {
                        LocalDate primeraConexion = null;
                        if (primeraConexionObj instanceof java.sql.Date) {
                            primeraConexion = ((java.sql.Date) primeraConexionObj).toLocalDate();
                        } else if (primeraConexionObj instanceof LocalDate) {
                            primeraConexion = (LocalDate) primeraConexionObj;
                        }
                        
                        if (primeraConexion != null) {
                            driver.setPrimeraConexionDate(primeraConexion);
                            long dias = java.time.temporal.ChronoUnit.DAYS.between(driver.getStartDate(), primeraConexion);
                            driver.setDiasRegistroAConexion((int) dias);
                        }
                    }
                    
                    Object primerViajeObj = fechas.get("primerViaje");
                    if (primerViajeObj != null) {
                        LocalDate primerViaje = null;
                        if (primerViajeObj instanceof java.sql.Date) {
                            primerViaje = ((java.sql.Date) primerViajeObj).toLocalDate();
                        } else if (primerViajeObj instanceof LocalDate) {
                            primerViaje = (LocalDate) primerViajeObj;
                        }
                        
                        if (primerViaje != null) {
                            driver.setPrimerViajeDate(primerViaje);
                            
                            if (driver.getPrimeraConexionDate() != null) {
                                Object primeraConexionTieneViajesObj = fechas.get("primeraConexionTieneViajes");
                                boolean primeraConexionTieneViajes = false;
                                if (primeraConexionTieneViajesObj != null) {
                                    if (primeraConexionTieneViajesObj instanceof Boolean) {
                                        primeraConexionTieneViajes = (Boolean) primeraConexionTieneViajesObj;
                                    } else if (primeraConexionTieneViajesObj instanceof Number) {
                                        primeraConexionTieneViajes = ((Number) primeraConexionTieneViajesObj).intValue() > 0;
                                    }
                                }
                                
                                if (primeraConexionTieneViajes) {
                                    driver.setDiasConexionAViaje(0);
                                } else {
                                    long diasConexionAViaje = java.time.temporal.ChronoUnit.DAYS.between(driver.getPrimeraConexionDate(), primerViaje);
                                    driver.setDiasConexionAViaje((int) diasConexionAViaje);
                                }
                            }
                        }
                    }
                }
                
                Object diasActivosObj = fechas != null ? fechas.get("diasActivos") : null;
                if (diasActivosObj != null && diasActivosObj instanceof Number) {
                    driver.setDiasActivos(((Number) diasActivosObj).intValue());
                }
                
                Object diasConectadosObj = fechas != null ? fechas.get("diasConectados") : null;
                if (diasConectadosObj != null && diasConectadosObj instanceof Number) {
                    driver.setDiasConectados(((Number) diasConectadosObj).intValue());
                }
                
                if (driver.getDiasConectados() != null && driver.getDiasConectados() > 0 && driver.getDiasActivos() != null) {
                    double tasa = (driver.getDiasActivos().doubleValue() / driver.getDiasConectados().doubleValue()) * 100.0;
                    driver.setTasaConversionConexion(tasa);
                }
                
                if (driver.getPrimerViajeDate() != null && driver.getStartDate() != null) {
                    LocalDate fecha25Viajes = calcularFecha25Viajes(driver.getDriverId(), driver.getPrimerViajeDate(), driver.getStartDate());
                    if (fecha25Viajes != null) {
                        long dias = java.time.temporal.ChronoUnit.DAYS.between(driver.getPrimerViajeDate(), fecha25Viajes);
                        driver.setDiasPrimerViajeA25Viajes((int) dias);
                    }
                }
                
                driver.setTieneLead(driver.getLeadCreatedAt() != null);
                driver.setTieneScout(driver.getHasScoutRegistration() != null && driver.getHasScoutRegistration());
                
                boolean matchScoreBajo = false;
                if (driver.getMatchScore() != null && driver.getMatchScore() < 0.7) {
                    matchScoreBajo = true;
                }
                if (driver.getScoutMatchScore() != null && driver.getScoutMatchScore() < 0.7) {
                    matchScoreBajo = true;
                }
                driver.setMatchScoreBajo(matchScoreBajo);
                
                boolean tieneInconsistencias = false;
                if (driver.getTieneLead() != null && driver.getTieneLead() && 
                    (driver.getTieneScout() == null || !driver.getTieneScout())) {
                    tieneInconsistencias = true;
                }
                if (driver.getTieneScout() != null && driver.getTieneScout() && 
                    (driver.getTieneLead() == null || !driver.getTieneLead())) {
                    tieneInconsistencias = true;
                }
                driver.setTieneInconsistencias(tieneInconsistencias);
                
                if (driver.getStartDate() != null) {
                    DayOfWeek dayOfWeek = driver.getStartDate().getDayOfWeek();
                    driver.setDiaSemanaRegistro(dayOfWeek.getDisplayName(TextStyle.FULL, new Locale("es", "ES")));
                    
                    int semanaDelMes = (driver.getStartDate().getDayOfMonth() - 1) / 7 + 1;
                    driver.setSemanaMesRegistro(semanaDelMes);
                    
                    int year = driver.getStartDate().getYear();
                    int weekOfYear = driver.getStartDate().get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
                    String semanaISO = String.format("%d-W%02d", year, weekOfYear);
                    driver.setSemanaISORegistro(semanaISO);
                }
            }
            
        } catch (Exception e) {
            logger.error("Error al calcular métricas de conversión", e);
        }
    }
    
    private LocalDate calcularFecha25Viajes(String driverId, LocalDate primerViajeDate, LocalDate startDate) {
        try {
            LocalDate fechaLimite = startDate.plusDays(14);
            
            StringBuilder sql = new StringBuilder();
            sql.append("WITH viajes_acumulados AS ( ");
            sql.append("  SELECT ");
            sql.append("    TO_DATE(sd.date_file, 'DD-MM-YYYY') as date, ");
            sql.append("    COALESCE(sd.count_orders_completed, 0) as viajes_dia, ");
            sql.append("    SUM(COALESCE(sd.count_orders_completed, 0)) OVER ( ");
            sql.append("      ORDER BY TO_DATE(sd.date_file, 'DD-MM-YYYY') ");
            sql.append("      ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW ");
            sql.append("    ) as viajes_acumulados ");
            sql.append("  FROM summary_daily sd ");
            sql.append("  WHERE sd.driver_id = ? ");
            sql.append("    AND TO_DATE(sd.date_file, 'DD-MM-YYYY') >= ? ");
            sql.append("    AND TO_DATE(sd.date_file, 'DD-MM-YYYY') <= ? ");
            sql.append("    AND COALESCE(sd.count_orders_completed, 0) > 0 ");
            sql.append("  ORDER BY TO_DATE(sd.date_file, 'DD-MM-YYYY') ");
            sql.append(") ");
            sql.append("SELECT date ");
            sql.append("FROM viajes_acumulados ");
            sql.append("WHERE viajes_acumulados >= 25 ");
            sql.append("ORDER BY date ");
            sql.append("LIMIT 1");
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql.toString(), 
                driverId, 
                java.sql.Date.valueOf(primerViajeDate),
                java.sql.Date.valueOf(fechaLimite)
            );
            
            if (!rows.isEmpty()) {
                Object dateObj = rows.get(0).get("date");
                if (dateObj instanceof java.sql.Date) {
                    return ((java.sql.Date) dateObj).toLocalDate();
                } else if (dateObj instanceof java.sql.Timestamp) {
                    return ((java.sql.Timestamp) dateObj).toLocalDateTime().toLocalDate();
                } else if (dateObj instanceof LocalDate) {
                    return (LocalDate) dateObj;
                }
            }
        } catch (Exception e) {
            logger.error("Error al calcular fecha de 25 viajes para driver {}", driverId, e);
        }
        return null;
    }
    
    public List<EvolutionMetricsDTO> getEvolutionMetrics(String parkId, String periodType, int periods) {
        parkId = parkId != null && !parkId.isEmpty() ? parkId : DEFAULT_PARK_ID;
        List<EvolutionMetricsDTO> results = new ArrayList<>();
        
        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate;
            
            if ("weeks".equals(periodType)) {
                startDate = endDate.minusWeeks(periods - 1).with(java.time.DayOfWeek.MONDAY);
            } else {
                startDate = endDate.minusMonths(periods - 1).withDayOfMonth(1);
            }
            
            StringBuilder sql = new StringBuilder();
            sql.append("WITH driver_metrics AS ( ");
            sql.append("  SELECT ");
            sql.append("    d.driver_id, ");
            sql.append("    d.hire_date, ");
            if ("weeks".equals(periodType)) {
                sql.append("    TO_CHAR(d.hire_date, 'IYYY') || '-W' || LPAD(TO_CHAR(d.hire_date, 'IW'), 2, '0') as period, ");
            } else {
                sql.append("    TO_CHAR(d.hire_date, 'YYYY-MM') as period, ");
            }
            sql.append("    CASE WHEN COUNT(CASE WHEN TO_DATE(sd.date_file, 'DD-MM-YYYY') >= d.hire_date AND TO_DATE(sd.date_file, 'DD-MM-YYYY') < d.hire_date + INTERVAL '14 days' AND COALESCE(sd.sum_work_time_seconds, 0) > 0 THEN 1 END) > 0 THEN true ELSE false END as has_connection, ");
            sql.append("    COALESCE(SUM(CASE WHEN TO_DATE(sd.date_file, 'DD-MM-YYYY') >= d.hire_date AND TO_DATE(sd.date_file, 'DD-MM-YYYY') < d.hire_date + INTERVAL '14 days' THEN COALESCE(sd.count_orders_completed, 0) ELSE 0 END), 0) as total_trips, ");
            sql.append("    COALESCE(SUM(CASE WHEN TO_DATE(sd.date_file, 'DD-MM-YYYY') >= d.hire_date AND TO_DATE(sd.date_file, 'DD-MM-YYYY') < d.hire_date + INTERVAL '14 days' AND COALESCE(sd.sum_work_time_seconds, 0) > 0 THEN 1 ELSE 0 END), 0) as dias_conectados, ");
            sql.append("    COALESCE(SUM(CASE WHEN TO_DATE(sd.date_file, 'DD-MM-YYYY') >= d.hire_date AND TO_DATE(sd.date_file, 'DD-MM-YYYY') < d.hire_date + INTERVAL '14 days' AND COALESCE(sd.count_orders_completed, 0) > 0 THEN 1 ELSE 0 END), 0) as dias_activos ");
            sql.append("  FROM drivers d ");
            sql.append("  LEFT JOIN summary_daily sd ON sd.driver_id = d.driver_id ");
            sql.append("  WHERE d.park_id = ? ");
            sql.append("    AND d.hire_date >= ? ");
            sql.append("    AND d.hire_date <= ? ");
            sql.append("  GROUP BY d.driver_id, d.hire_date ");
            sql.append(") ");
            sql.append("SELECT ");
            sql.append("  period, ");
            sql.append("  COUNT(*) as total_drivers, ");
            sql.append("  COUNT(CASE WHEN NOT has_connection AND total_trips = 0 THEN 1 END) as solo_registro, ");
            sql.append("  COUNT(CASE WHEN has_connection AND total_trips = 0 THEN 1 END) as conecto_sin_viajes, ");
            sql.append("  COUNT(CASE WHEN total_trips > 0 THEN 1 END) as activo_con_viajes, ");
            sql.append("  COUNT(CASE WHEN has_connection THEN 1 END) as conectados, ");
            sql.append("  AVG(CASE WHEN total_trips >= 1 THEN 1.0 ELSE 0.0 END) * 100 as tasa_1_viaje, ");
            sql.append("  AVG(CASE WHEN total_trips >= 5 THEN 1.0 ELSE 0.0 END) * 100 as tasa_5_viajes, ");
            sql.append("  AVG(CASE WHEN total_trips >= 25 THEN 1.0 ELSE 0.0 END) * 100 as tasa_25_viajes, ");
            sql.append("  SUM(total_trips) as total_viajes, ");
            sql.append("  AVG(CASE WHEN total_trips > 0 THEN total_trips ELSE NULL END) as promedio_viajes_activos ");
            sql.append("FROM driver_metrics ");
            sql.append("GROUP BY period ");
            sql.append("ORDER BY period");
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                sql.toString(), 
                parkId, 
                java.sql.Date.valueOf(startDate),
                java.sql.Date.valueOf(endDate)
            );
            
            for (Map<String, Object> row : rows) {
                EvolutionMetricsDTO dto = new EvolutionMetricsDTO();
                dto.setPeriod((String) row.get("period"));
                
                Number totalDrivers = (Number) row.get("total_drivers");
                dto.setTotalDrivers(totalDrivers != null ? totalDrivers.intValue() : 0);
                
                Number soloRegistro = (Number) row.get("solo_registro");
                dto.setSoloRegistro(soloRegistro != null ? soloRegistro.intValue() : 0);
                
                Number conectoSinViajes = (Number) row.get("conecto_sin_viajes");
                dto.setConectoSinViajes(conectoSinViajes != null ? conectoSinViajes.intValue() : 0);
                
                Number activoConViajes = (Number) row.get("activo_con_viajes");
                dto.setActivoConViajes(activoConViajes != null ? activoConViajes.intValue() : 0);
                
                Number conectados = (Number) row.get("conectados");
                int total = dto.getTotalDrivers();
                int conectadosCount = conectados != null ? conectados.intValue() : 0;
                
                dto.setTasaRegistroAConexion(total > 0 ? (conectadosCount * 100.0 / total) : 0.0);
                dto.setTasaConexionAViaje(conectadosCount > 0 ? (dto.getActivoConViajes() * 100.0 / conectadosCount) : 0.0);
                
                Number tasa1Viaje = (Number) row.get("tasa_1_viaje");
                dto.setTasaAlcanzo1Viaje(tasa1Viaje != null ? tasa1Viaje.doubleValue() : 0.0);
                
                Number tasa5Viajes = (Number) row.get("tasa_5_viajes");
                dto.setTasaAlcanzo5Viajes(tasa5Viajes != null ? tasa5Viajes.doubleValue() : 0.0);
                
                Number tasa25Viajes = (Number) row.get("tasa_25_viajes");
                dto.setTasaAlcanzo25Viajes(tasa25Viajes != null ? tasa25Viajes.doubleValue() : 0.0);
                
                Number totalViajes = (Number) row.get("total_viajes");
                dto.setTotalViajes(totalViajes != null ? totalViajes.intValue() : 0);
                
                Number promedioViajes = (Number) row.get("promedio_viajes_activos");
                dto.setPromedioViajesPorActivo(promedioViajes != null ? promedioViajes.doubleValue() : 0.0);
                
                dto.setPromedioDiasRegistroAConexion(0.0);
                dto.setPromedioDiasConexionAViaje(0.0);
                dto.setPromedioDiasPrimerViajeA25Viajes(0.0);
                
                results.add(dto);
            }
            
        } catch (Exception e) {
            logger.error("Error al calcular métricas de evolución", e);
        }
        
        return results;
    }
}

