package com.yego.contractortracker.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/inspect")
public class InspectController {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @GetMapping("/drivers")
    public ResponseEntity<List<Map<String, Object>>> inspectDrivers() {
        String sql = "SELECT * FROM drivers LIMIT 5";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
        return ResponseEntity.ok(results);
    }
    
    @GetMapping("/summary-daily")
    public ResponseEntity<List<Map<String, Object>>> inspectSummaryDaily() {
        String sql = "SELECT * FROM summary_daily LIMIT 5";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
        return ResponseEntity.ok(results);
    }
    
    @GetMapping("/tracking-history")
    public ResponseEntity<List<Map<String, Object>>> inspectTrackingHistory() {
        String sql = "SELECT * FROM contractor_tracking_history ORDER BY calculation_date DESC LIMIT 10";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
        return ResponseEntity.ok(results);
    }
    
    @GetMapping("/milestone-match-example")
    public ResponseEntity<Map<String, Object>> inspectMilestoneMatchExample(
            @RequestParam(required = false, defaultValue = "08e20910d81d42658d4334d3f6d10ac0") String parkId) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String sql = "SELECT d.driver_id, d.hire_date, " +
                    "  sd.date_file, sd.count_orders_completed, " +
                    "  TO_DATE(sd.date_file, 'DD-MM-YYYY') as fecha_parseada, " +
                    "  (TO_DATE(sd.date_file, 'DD-MM-YYYY') - d.hire_date) as dias_desde_hire " +
                    "FROM drivers d " +
                    "INNER JOIN summary_daily sd ON sd.driver_id = d.driver_id " +
                    "WHERE d.park_id = ? " +
                    "  AND d.hire_date >= (CURRENT_DATE - INTERVAL '30 day') " +
                    "  AND d.hire_date <= CURRENT_DATE " +
                    "  AND TO_DATE(sd.date_file, 'DD-MM-YYYY') >= d.hire_date " +
                    "  AND TO_DATE(sd.date_file, 'DD-MM-YYYY') <= (d.hire_date + INTERVAL '14 day') " +
                    "ORDER BY d.hire_date DESC, TO_DATE(sd.date_file, 'DD-MM-YYYY') ASC " +
                    "LIMIT 20";
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, parkId);
            response.put("success", true);
            response.put("count", results.size());
            response.put("data", results);
            response.put("description", "Ejemplos de match entre drivers y summary_daily para cálculo de milestones");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/milestone-aggregate-example")
    public ResponseEntity<Map<String, Object>> inspectMilestoneAggregateExample(
            @RequestParam(required = false, defaultValue = "08e20910d81d42658d4334d3f6d10ac0") String parkId,
            @RequestParam(required = false, defaultValue = "7") int periodDays) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String sql = "SELECT d.driver_id, d.hire_date, " +
                    "  SUM(COALESCE(sd.count_orders_completed, 0)) as total_viajes, " +
                    "  COUNT(DISTINCT sd.date_file) as dias_con_viajes " +
                    "FROM drivers d " +
                    "INNER JOIN summary_daily sd ON sd.driver_id = d.driver_id " +
                    "WHERE d.park_id = ? " +
                    "  AND d.hire_date >= (CURRENT_DATE - INTERVAL '30 day') " +
                    "  AND d.hire_date <= CURRENT_DATE " +
                    "  AND TO_DATE(sd.date_file, 'DD-MM-YYYY') >= d.hire_date " +
                    "  AND TO_DATE(sd.date_file, 'DD-MM-YYYY') <= (d.hire_date + INTERVAL '" + periodDays + " day') " +
                    "GROUP BY d.driver_id, d.hire_date " +
                    "HAVING SUM(COALESCE(sd.count_orders_completed, 0)) > 0 " +
                    "ORDER BY total_viajes DESC, d.hire_date DESC " +
                    "LIMIT 10";
            
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, parkId);
            response.put("success", true);
            response.put("periodDays", periodDays);
            response.put("count", results.size());
            response.put("data", results);
            response.put("description", "Agregación de viajes por driver en período de " + periodDays + " días desde hire_date");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/init-tracking-history-table")
    public ResponseEntity<Map<String, Object>> initializeTrackingHistoryTable() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String createTableSql = "CREATE TABLE IF NOT EXISTS contractor_tracking_history (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "driver_id VARCHAR(255) NOT NULL, " +
                "park_id VARCHAR(255) NOT NULL, " +
                "calculation_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "total_trips_historical INTEGER NOT NULL DEFAULT 0, " +
                "sum_work_time_seconds BIGINT, " +
                "has_historical_connection BOOLEAN NOT NULL DEFAULT FALSE, " +
                "status_registered BOOLEAN NOT NULL DEFAULT FALSE, " +
                "status_connected BOOLEAN NOT NULL DEFAULT FALSE, " +
                "status_with_trips BOOLEAN NOT NULL DEFAULT FALSE, " +
                "last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                ")";
            
            jdbcTemplate.execute(createTableSql);
            response.put("status", "success");
            response.put("message", "Tabla contractor_tracking_history creada exitosamente");
            
            String[] indexStatements = {
                "CREATE INDEX IF NOT EXISTS idx_tracking_driver_id ON contractor_tracking_history(driver_id)",
                "CREATE INDEX IF NOT EXISTS idx_tracking_park_id ON contractor_tracking_history(park_id)",
                "CREATE INDEX IF NOT EXISTS idx_tracking_calculation_date ON contractor_tracking_history(calculation_date DESC)",
                "CREATE INDEX IF NOT EXISTS idx_tracking_last_updated ON contractor_tracking_history(last_updated DESC)"
            };
            
            for (String indexSql : indexStatements) {
                try {
                    jdbcTemplate.execute(indexSql);
                } catch (Exception e) {
                    System.out.println("Advertencia al crear índice: " + e.getMessage());
                }
            }
            
            String uniqueIndexSql = "CREATE UNIQUE INDEX IF NOT EXISTS idx_tracking_driver_date ON contractor_tracking_history(driver_id, calculation_date)";
            try {
                jdbcTemplate.execute(uniqueIndexSql);
            } catch (Exception e) {
                System.out.println("Advertencia al crear índice único: " + e.getMessage());
            }
            
            response.put("message", "Tabla e índices creados exitosamente");
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error al crear tabla: " + e.getMessage());
            response.put("error", e.getClass().getSimpleName());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/compare-milestones-25")
    public ResponseEntity<Map<String, Object>> compareMilestones25(
            @RequestParam(required = false, defaultValue = "08e20910d81d42658d4334d3f6d10ac0") String parkId,
            @RequestParam(required = false, defaultValue = "14") int periodDays) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String sqlDirecta = "SELECT d.driver_id, d.park_id, d.hire_date, " +
                    "  SUM(COALESCE(sd.count_orders_completed, 0)) as total_viajes " +
                    "FROM drivers d " +
                    "INNER JOIN summary_daily sd ON sd.driver_id = d.driver_id " +
                    "WHERE d.park_id = ? " +
                    "  AND TO_DATE(sd.date_file, 'DD-MM-YYYY') >= d.hire_date::DATE " +
                    "  AND TO_DATE(sd.date_file, 'DD-MM-YYYY') <= (d.hire_date::DATE + INTERVAL '" + periodDays + " day') " +
                    "GROUP BY d.driver_id, d.park_id, d.hire_date " +
                    "HAVING SUM(COALESCE(sd.count_orders_completed, 0)) >= 25";
            
            List<Map<String, Object>> sqlResults = jdbcTemplate.queryForList(sqlDirecta, parkId);
            
            String tablaSql = "SELECT driver_id, trip_count FROM milestone_instances WHERE park_id = ? AND milestone_type = 25 AND period_days = ?";
            List<Map<String, Object>> tablaResults = jdbcTemplate.queryForList(tablaSql, parkId, periodDays);
            
            Map<String, Integer> tablaMap = new HashMap<>();
            for (Map<String, Object> row : tablaResults) {
                tablaMap.put((String) row.get("driver_id"), ((Number) row.get("trip_count")).intValue());
            }
            
            List<Map<String, Object>> faltantes = new ArrayList<>();
            List<Map<String, Object>> diferencias = new ArrayList<>();
            
            for (Map<String, Object> row : sqlResults) {
                String driverId = (String) row.get("driver_id");
                Integer totalViajes = ((Number) row.get("total_viajes")).intValue();
                
                if (!tablaMap.containsKey(driverId)) {
                    Map<String, Object> faltante = new HashMap<>();
                    faltante.put("driver_id", driverId);
                    faltante.put("total_viajes", totalViajes);
                    faltante.put("hire_date", row.get("hire_date"));
                    faltantes.add(faltante);
                } else {
                    Integer viajesEnTabla = tablaMap.get(driverId);
                    if (!viajesEnTabla.equals(totalViajes)) {
                        Map<String, Object> diferencia = new HashMap<>();
                        diferencia.put("driver_id", driverId);
                        diferencia.put("viajes_sql", totalViajes);
                        diferencia.put("viajes_tabla", viajesEnTabla);
                        diferencia.put("diferencia", totalViajes - viajesEnTabla);
                        diferencias.add(diferencia);
                    }
                }
            }
            
            response.put("success", true);
            response.put("totalEnSQL", sqlResults.size());
            response.put("totalEnTabla", tablaResults.size());
            response.put("faltantes", faltantes.size());
            response.put("diferencias", diferencias.size());
            response.put("detalleFaltantes", faltantes);
            response.put("detalleDiferencias", diferencias);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("errorClass", e.getClass().getName());
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/sync-milestones-25")
    public ResponseEntity<Map<String, Object>> syncMilestones25(
            @RequestParam(required = false, defaultValue = "08e20910d81d42658d4334d3f6d10ac0") String parkId,
            @RequestParam(required = false, defaultValue = "14") int periodDays) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String countSql = "SELECT COUNT(*) FROM milestone_instances WHERE park_id = ? AND milestone_type = 25 AND period_days = ?";
            Integer countEnTabla = jdbcTemplate.queryForObject(countSql, Integer.class, parkId, periodDays);
            
            String sqlDirecta = "SELECT d.driver_id, d.park_id, d.hire_date, " +
                    "  SUM(COALESCE(sd.count_orders_completed, 0)) as total_viajes " +
                    "FROM drivers d " +
                    "INNER JOIN summary_daily sd ON sd.driver_id = d.driver_id " +
                    "WHERE d.park_id = ? " +
                    "  AND TO_DATE(sd.date_file, 'DD-MM-YYYY') >= d.hire_date::DATE " +
                    "  AND TO_DATE(sd.date_file, 'DD-MM-YYYY') <= (d.hire_date::DATE + INTERVAL '" + periodDays + " day') " +
                    "GROUP BY d.driver_id, d.park_id, d.hire_date " +
                    "HAVING SUM(COALESCE(sd.count_orders_completed, 0)) >= 25 " +
                    "ORDER BY total_viajes DESC";
            
            List<Map<String, Object>> sqlResults = jdbcTemplate.queryForList(sqlDirecta, parkId);
            
            int insertados = 0;
            int actualizados = 0;
            int totalSql = sqlResults.size();
            
            for (Map<String, Object> row : sqlResults) {
                String driverId = (String) row.get("driver_id");
                String driverParkId = (String) row.get("park_id");
                Number totalViajes = (Number) row.get("total_viajes");
                
                String checkSql = "SELECT id FROM milestone_instances WHERE driver_id = ? AND milestone_type = 25 AND period_days = ? AND park_id = ?";
                List<Map<String, Object>> existing = jdbcTemplate.queryForList(checkSql, driverId, 25, periodDays, parkId);
                
                if (existing.isEmpty()) {
                    String insertSql = "INSERT INTO milestone_instances (driver_id, park_id, milestone_type, period_days, trip_count, fulfillment_date, calculation_date) " +
                            "VALUES (?, ?, 25, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
                    jdbcTemplate.update(insertSql, driverId, driverParkId, periodDays, totalViajes.intValue());
                    insertados++;
                } else {
                    String updateSql = "UPDATE milestone_instances SET trip_count = ?, calculation_date = CURRENT_TIMESTAMP " +
                            "WHERE driver_id = ? AND milestone_type = 25 AND period_days = ? AND park_id = ?";
                    jdbcTemplate.update(updateSql, totalViajes.intValue(), driverId, periodDays, parkId);
                    actualizados++;
                }
            }
            
            Integer countFinal = jdbcTemplate.queryForObject(countSql, Integer.class, parkId, periodDays);
            
            response.put("success", true);
            response.put("totalEnSQL", totalSql);
            response.put("totalEnTablaAntes", countEnTabla);
            response.put("totalEnTablaDespues", countFinal);
            response.put("insertados", insertados);
            response.put("actualizados", actualizados);
            response.put("message", "Sincronización completada exitosamente");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("errorClass", e.getClass().getName());
            if (e.getCause() != null) {
                response.put("cause", e.getCause().getMessage());
            }
            return ResponseEntity.status(500).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
}

