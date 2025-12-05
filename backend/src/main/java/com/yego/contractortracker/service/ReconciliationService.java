package com.yego.contractortracker.service;

import com.yego.contractortracker.dto.ReconciliationSummaryDTO;
import com.yego.contractortracker.entity.MilestoneInstance;
import com.yego.contractortracker.entity.ScoutRegistration;
import com.yego.contractortracker.entity.YangoTransaction;
import com.yego.contractortracker.repository.MilestoneInstanceRepository;
import com.yego.contractortracker.repository.ScoutRegistrationRepository;
import com.yego.contractortracker.repository.YangoTransactionRepository;
import com.yego.contractortracker.util.WeekISOUtil;
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
public class ReconciliationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ReconciliationService.class);
    private static final String DEFAULT_PARK_ID = "08e20910d81d42658d4334d3f6d10ac0";
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private ScoutRegistrationRepository scoutRegistrationRepository;
    
    @Autowired
    private MilestoneInstanceRepository milestoneInstanceRepository;
    
    @Autowired
    private YangoTransactionRepository yangoTransactionRepository;
    
    @Transactional(readOnly = true)
    public List<ReconciliationSummaryDTO> obtenerResumenConsolidado(
            String periodType,
            LocalDate dateFrom,
            LocalDate dateTo,
            List<String> weekISOs,
            String parkId,
            String scoutId,
            String channel) {
        
        parkId = parkId != null && !parkId.isEmpty() ? parkId : DEFAULT_PARK_ID;
        
        List<LocalDate> periods = new ArrayList<>();
        
        if (weekISOs != null && !weekISOs.isEmpty()) {
            for (String weekISO : weekISOs) {
                LocalDate[] weekRange = WeekISOUtil.getWeekRange(weekISO);
                if (weekRange != null && weekRange.length == 2) {
                    periods.add(weekRange[0]);
                }
            }
            if (!periods.isEmpty()) {
                dateFrom = periods.get(0);
                dateTo = periods.get(periods.size() - 1).plusDays(6);
            }
        } else if (dateFrom == null || dateTo == null) {
            dateFrom = LocalDate.now().minusDays(30);
            dateTo = LocalDate.now();
        }
        
        if (periods.isEmpty()) {
            if ("day".equals(periodType)) {
                LocalDate current = dateFrom;
                while (!current.isAfter(dateTo)) {
                    periods.add(current);
                    current = current.plusDays(1);
                }
            } else {
                LocalDate current = dateFrom;
                while (!current.isAfter(dateTo)) {
                    LocalDate weekStart = current.with(java.time.DayOfWeek.MONDAY);
                    LocalDate weekEnd = weekStart.plusDays(6);
                    if (weekEnd.isAfter(dateTo)) {
                        weekEnd = dateTo;
                    }
                    periods.add(weekStart);
                    current = weekEnd.plusDays(1);
                }
            }
        }
        
        List<ReconciliationSummaryDTO> summaries = new ArrayList<>();
        
        for (LocalDate period : periods) {
            LocalDate periodEnd = "day".equals(periodType) ? period : period.plusDays(6);
            if (periodEnd.isAfter(dateTo)) {
                periodEnd = dateTo;
            }
            
            ReconciliationSummaryDTO summary = calcularResumenParaPeriodo(period, periodEnd, periodType, parkId, scoutId, channel);
            summaries.add(summary);
        }
        
        return summaries;
    }
    
    private ReconciliationSummaryDTO calcularResumenParaPeriodo(
            LocalDate periodStart,
            LocalDate periodEnd,
            String periodType,
            String parkId,
            String scoutId,
            String channel) {
        
        ReconciliationSummaryDTO summary = new ReconciliationSummaryDTO();
        summary.setPeriod(periodType.equals("day") ? periodStart.toString() : 
            String.format("%04d-W%02d", periodStart.get(java.time.temporal.WeekFields.ISO.weekBasedYear()),
                periodStart.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())));
        summary.setPeriodType(periodType);
        
        ReconciliationSummaryDTO.TotalsDTO totals = new ReconciliationSummaryDTO.TotalsDTO();
        
        String sql = "SELECT COUNT(DISTINCT d.driver_id) as total " +
                     "FROM drivers d " +
                     "WHERE d.park_id = ? AND d.hire_date BETWEEN ? AND ?";
        
        Long registrados = jdbcTemplate.queryForObject(sql, Long.class, parkId, periodStart, periodEnd);
        totals.setRegistrados(registrados != null ? registrados : 0L);
        
        sql = "SELECT COUNT(DISTINCT d.driver_id) as total " +
              "FROM drivers d " +
              "INNER JOIN lead_matches lm ON d.driver_id = lm.driver_id " +
              "LEFT JOIN ( " +
              "  SELECT DISTINCT ON (driver_id) driver_id, acquisition_channel " +
              "  FROM contractor_tracking_history " +
              "  ORDER BY driver_id, calculation_date DESC " +
              ") cth ON cth.driver_id = d.driver_id " +
              "WHERE d.park_id = ? AND d.hire_date BETWEEN ? AND ? " +
              "AND COALESCE(cth.acquisition_channel, '') = 'cabinet'";
        
        Long porCabinet = jdbcTemplate.queryForObject(sql, Long.class, parkId, periodStart, periodEnd);
        totals.setPorCabinet(porCabinet != null ? porCabinet : 0L);
        totals.setPorOtrosMedios(totals.getRegistrados() - totals.getPorCabinet());
        
        sql = "SELECT COUNT(DISTINCT d.driver_id) as total " +
              "FROM drivers d " +
              "INNER JOIN summary_daily sd ON d.driver_id = sd.driver_id " +
              "WHERE d.park_id = ? AND d.hire_date BETWEEN ? AND ? " +
              "AND COALESCE(sd.sum_work_time_seconds, 0) > 0";
        
        Long conectados = jdbcTemplate.queryForObject(sql, Long.class, parkId, periodStart, periodEnd);
        totals.setConectados(conectados != null ? conectados : 0L);
        
        sql = "SELECT COUNT(DISTINCT d.driver_id) as total " +
              "FROM drivers d " +
              "INNER JOIN summary_daily sd ON d.driver_id = sd.driver_id " +
              "WHERE d.park_id = ? AND d.hire_date BETWEEN ? AND ? " +
              "AND COALESCE(sd.count_orders_completed, 0) > 0 " +
              "AND TO_DATE(sd.date_file, 'DD-MM-YYYY') >= d.hire_date " +
              "AND TO_DATE(sd.date_file, 'DD-MM-YYYY') < d.hire_date + INTERVAL '7 days'";
        
        Long conViajes7d = jdbcTemplate.queryForObject(sql, Long.class, parkId, periodStart, periodEnd);
        totals.setConViajes7d(conViajes7d != null ? conViajes7d : 0L);
        
        sql = "SELECT COUNT(DISTINCT d.driver_id) as total " +
              "FROM drivers d " +
              "INNER JOIN summary_daily sd ON d.driver_id = sd.driver_id " +
              "WHERE d.park_id = ? AND d.hire_date BETWEEN ? AND ? " +
              "AND COALESCE(sd.count_orders_completed, 0) > 0 " +
              "AND TO_DATE(sd.date_file, 'DD-MM-YYYY') >= d.hire_date " +
              "AND TO_DATE(sd.date_file, 'DD-MM-YYYY') < d.hire_date + INTERVAL '14 days'";
        
        Long conViajes14d = jdbcTemplate.queryForObject(sql, Long.class, parkId, periodStart, periodEnd);
        totals.setConViajes14d(conViajes14d != null ? conViajes14d : 0L);
        
        List<MilestoneInstance> milestones1 = milestoneInstanceRepository.findByParkIdAndMilestoneTypeAndPeriodDays(
            parkId, 1, 14);
        List<MilestoneInstance> milestones5 = milestoneInstanceRepository.findByParkIdAndMilestoneTypeAndPeriodDays(
            parkId, 5, 14);
        List<MilestoneInstance> milestones25 = milestoneInstanceRepository.findByParkIdAndMilestoneTypeAndPeriodDays(
            parkId, 25, 14);
        
        totals.setConMilestone1((long) milestones1.stream()
            .filter(m -> {
                LocalDate fulfillmentDate = m.getFulfillmentDate().toLocalDate();
                return !fulfillmentDate.isBefore(periodStart) && !fulfillmentDate.isAfter(periodEnd);
            })
            .count());
        totals.setConMilestone5((long) milestones5.stream()
            .filter(m -> {
                LocalDate fulfillmentDate = m.getFulfillmentDate().toLocalDate();
                return !fulfillmentDate.isBefore(periodStart) && !fulfillmentDate.isAfter(periodEnd);
            })
            .count());
        totals.setConMilestone25((long) milestones25.stream()
            .filter(m -> {
                LocalDate fulfillmentDate = m.getFulfillmentDate().toLocalDate();
                return !fulfillmentDate.isBefore(periodStart) && !fulfillmentDate.isAfter(periodEnd);
            })
            .count());
        
        List<YangoTransaction> transacciones = yangoTransactionRepository.findAll().stream()
            .filter(t -> {
                if (!t.getIsMatched()) return false;
                LocalDate transactionDate = t.getTransactionDate().toLocalDate();
                return transactionDate.isAfter(periodStart.minusDays(1)) && !transactionDate.isAfter(periodEnd);
            })
            .collect(Collectors.toList());
        
        totals.setConPagoYango((long) transacciones.size());
        
        summary.setTotals(totals);
        
        List<ReconciliationSummaryDTO.ScoutSummaryDTO> byScout = calcularResumenPorScout(
            periodStart, periodEnd, parkId);
        summary.setByScout(byScout);
        
        ReconciliationSummaryDTO.ConversionMetricsDTO metrics = calcularMetricasConversion(totals);
        summary.setConversionMetrics(metrics);
        
        ReconciliationSummaryDTO.InconsistenciesDTO inconsistencies = calcularInconsistencias(
            periodStart, periodEnd, parkId);
        summary.setInconsistencies(inconsistencies);
        
        String sqlLastUpdated = "SELECT MAX(last_updated) FROM lead_matches";
        try {
            LocalDateTime lastUpdated = jdbcTemplate.queryForObject(sqlLastUpdated, LocalDateTime.class);
            summary.setLastUpdated(lastUpdated != null ? lastUpdated.toString() : LocalDateTime.now().toString());
        } catch (Exception e) {
            summary.setLastUpdated(LocalDateTime.now().toString());
        }
        
        return summary;
    }
    
    private List<ReconciliationSummaryDTO.ScoutSummaryDTO> calcularResumenPorScout(
            LocalDate periodStart,
            LocalDate periodEnd,
            String parkId) {
        
        List<ScoutRegistration> registrations = scoutRegistrationRepository.findByRegistrationDateBetween(
            periodStart, periodEnd);
        
        Map<String, ReconciliationSummaryDTO.ScoutSummaryDTO> scoutMap = new HashMap<>();
        
        for (ScoutRegistration reg : registrations) {
            String scoutId = reg.getScoutId();
            ReconciliationSummaryDTO.ScoutSummaryDTO summary = scoutMap.getOrDefault(scoutId,
                new ReconciliationSummaryDTO.ScoutSummaryDTO(scoutId, scoutId, 0L, 0L, 0L, 0L, 0L, 0L));
            
            summary.setCount(summary.getCount() + 1);
            summary.setRegistrados(summary.getRegistrados() + 1);
            
            if (reg.getIsMatched() && reg.getDriverId() != null) {
                String sql = "SELECT COUNT(*) FROM summary_daily sd " +
                             "WHERE sd.driver_id = ? AND COALESCE(sd.sum_work_time_seconds, 0) > 0";
                Long conectado = jdbcTemplate.queryForObject(sql, Long.class, reg.getDriverId());
                if (conectado != null && conectado > 0) {
                    summary.setConectados(summary.getConectados() + 1);
                }
                
                sql = "SELECT COUNT(*) FROM summary_daily sd " +
                      "WHERE sd.driver_id = ? AND COALESCE(sd.count_orders_completed, 0) > 0";
                Long conViajes = jdbcTemplate.queryForObject(sql, Long.class, reg.getDriverId());
                if (conViajes != null && conViajes > 0) {
                    summary.setConViajes(summary.getConViajes() + 1);
                }
                
                List<MilestoneInstance> milestones = milestoneInstanceRepository.findByDriverId(reg.getDriverId());
                if (!milestones.isEmpty()) {
                    summary.setConMilestones(summary.getConMilestones() + 1);
                }
                
                List<YangoTransaction> transacciones = yangoTransactionRepository.findByDriverId(reg.getDriverId());
                if (!transacciones.isEmpty()) {
                    summary.setConPago(summary.getConPago() + 1);
                }
            }
            
            scoutMap.put(scoutId, summary);
        }
        
        return new ArrayList<>(scoutMap.values());
    }
    
    private ReconciliationSummaryDTO.ConversionMetricsDTO calcularMetricasConversion(
            ReconciliationSummaryDTO.TotalsDTO totals) {
        
        ReconciliationSummaryDTO.ConversionMetricsDTO metrics = 
            new ReconciliationSummaryDTO.ConversionMetricsDTO();
        
        if (totals.getRegistrados() > 0) {
            metrics.setTasaConexion(totals.getConectados().doubleValue() / totals.getRegistrados().doubleValue() * 100);
            metrics.setTasaActivacion(totals.getConViajes14d().doubleValue() / totals.getRegistrados().doubleValue() * 100);
            metrics.setTasaMilestone1(totals.getConMilestone1().doubleValue() / totals.getRegistrados().doubleValue() * 100);
            metrics.setTasaMilestone5(totals.getConMilestone5().doubleValue() / totals.getRegistrados().doubleValue() * 100);
            metrics.setTasaMilestone25(totals.getConMilestone25().doubleValue() / totals.getRegistrados().doubleValue() * 100);
        } else {
            metrics.setTasaConexion(0.0);
            metrics.setTasaActivacion(0.0);
            metrics.setTasaMilestone1(0.0);
            metrics.setTasaMilestone5(0.0);
            metrics.setTasaMilestone25(0.0);
        }
        
        long totalMilestones = totals.getConMilestone1() + totals.getConMilestone5() + totals.getConMilestone25();
        if (totalMilestones > 0) {
            metrics.setTasaPagoYango(totals.getConPagoYango().doubleValue() / totalMilestones * 100);
        } else {
            metrics.setTasaPagoYango(0.0);
        }
        
        return metrics;
    }
    
    private ReconciliationSummaryDTO.InconsistenciesDTO calcularInconsistencias(
            LocalDate periodStart,
            LocalDate periodEnd,
            String parkId) {
        
        ReconciliationSummaryDTO.InconsistenciesDTO inconsistencies = 
            new ReconciliationSummaryDTO.InconsistenciesDTO();
        
        String sqlLeads = "SELECT COUNT(*) as total " +
                          "FROM lead_matches lm " +
                          "WHERE lm.is_discarded = false " +
                          "AND (lm.driver_id IS NULL OR lm.driver_id = '') " +
                          "AND lm.lead_created_at BETWEEN ? AND ?";
        
        Long sinMatch = jdbcTemplate.queryForObject(sqlLeads, Long.class, periodStart, periodEnd);
        inconsistencies.setSinMatch(sinMatch != null ? sinMatch : 0L);
        
        List<MilestoneInstance> milestones = milestoneInstanceRepository.findAll().stream()
            .filter(m -> {
                if (!m.getParkId().equals(parkId)) return false;
                LocalDate fulfillmentDate = m.getFulfillmentDate().toLocalDate();
                return !fulfillmentDate.isBefore(periodStart) && !fulfillmentDate.isAfter(periodEnd);
            })
            .collect(Collectors.toList());
        
        long milestoneSinPago = milestones.stream()
            .filter(m -> {
                List<YangoTransaction> trans = yangoTransactionRepository.findByDriverId(m.getDriverId());
                return trans.stream().noneMatch(t -> 
                    t.getMilestoneInstanceId() != null && 
                    t.getMilestoneInstanceId().equals(m.getId()));
            })
            .count();
        inconsistencies.setMilestoneSinPago(milestoneSinPago);
        
        List<YangoTransaction> transacciones = yangoTransactionRepository.findAll().stream()
            .filter(t -> {
                if (!t.getIsMatched()) return false;
                LocalDate transactionDate = t.getTransactionDate().toLocalDate();
                return transactionDate.isAfter(periodStart.minusDays(1)) && !transactionDate.isAfter(periodEnd);
            })
            .collect(Collectors.toList());
        
        long pagoSinMilestone = transacciones.stream()
            .filter(t -> t.getMilestoneInstanceId() == null)
            .count();
        inconsistencies.setPagoSinMilestone(pagoSinMilestone);
        
        inconsistencies.setSinPago(milestoneSinPago);
        
        return inconsistencies;
    }
    
    @Transactional(readOnly = true)
    public ReconciliationSummaryDTO obtenerCierreDiaAnterior(String parkId) {
        parkId = parkId != null && !parkId.isEmpty() ? parkId : DEFAULT_PARK_ID;
        LocalDate ayer = LocalDate.now().minusDays(1);
        return calcularResumenParaPeriodo(ayer, ayer, "day", parkId, null, null);
    }
}

