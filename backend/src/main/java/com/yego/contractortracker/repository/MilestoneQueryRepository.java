package com.yego.contractortracker.repository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class MilestoneQueryRepository {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    public List<Map<String, Object>> obtenerDriversConViajesPorPeriodo(String parkId, int periodDays, LocalDate hireDateFrom, LocalDate hireDateTo) {
        List<Object> params = new ArrayList<>();
        
        String sql = "SELECT d.driver_id, d.park_id, d.hire_date, " +
                "  SUM(COALESCE(sd.count_orders_completed, 0)) as total_viajes " +
                "FROM drivers d " +
                "INNER JOIN summary_daily sd ON sd.driver_id = d.driver_id " +
                "WHERE d.park_id = ? ";
        params.add(parkId);
        
        if (hireDateFrom != null) {
            sql += "  AND d.hire_date::DATE >= ? ";
            params.add(hireDateFrom);
        }
        
        if (hireDateTo != null) {
            sql += "  AND d.hire_date::DATE <= ? ";
            params.add(hireDateTo);
        }
        
        sql += "  AND TO_DATE(sd.date_file, 'DD-MM-YYYY') >= d.hire_date::DATE " +
                "  AND TO_DATE(sd.date_file, 'DD-MM-YYYY') <= (d.hire_date::DATE + INTERVAL '" + periodDays + " day') " +
                "GROUP BY d.driver_id, d.park_id, d.hire_date " +
                "HAVING SUM(COALESCE(sd.count_orders_completed, 0)) >= 1 " +
                "ORDER BY total_viajes DESC, d.hire_date DESC, d.driver_id";
        
        return jdbcTemplate.queryForList(sql, params.toArray());
    }
}

