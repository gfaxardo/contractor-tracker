import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class EjecutarOptimizacion {
    public static void main(String[] args) {
        String host = System.getenv("DB_HOST");
        if (host == null) host = "168.119.226.236";
        
        String port = System.getenv("DB_PORT");
        if (port == null) port = "5432";
        
        String dbName = System.getenv("DB_NAME");
        if (dbName == null) dbName = "yego_integral";
        
        String user = System.getenv("DB_USER");
        if (user == null) user = "yego_user";
        
        String password = System.getenv("DB_PASSWORD");
        if (password == null) password = "37>MNA&-35+";
        
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
        
        System.out.println("Conectando a: " + url);
        System.out.println("Usuario: " + user);
        System.out.println("==========================================");
        
        String[] sqlStatements = {
            "-- 1. ÍNDICE FUNCIONAL CRÍTICO",
            "CREATE INDEX IF NOT EXISTS idx_summary_daily_date_file_date ON summary_daily(TO_DATE(date_file, 'DD-MM-YYYY'))",
            
            "-- 2. Índice compuesto para optimizar JOINs",
            "CREATE INDEX IF NOT EXISTS idx_summary_daily_driver_date_file ON summary_daily(driver_id, TO_DATE(date_file, 'DD-MM-YYYY'))",
            
            "-- 3. Índice compuesto para agregaciones",
            "CREATE INDEX IF NOT EXISTS idx_summary_daily_driver_date_metrics ON summary_daily(driver_id, TO_DATE(date_file, 'DD-MM-YYYY'), sum_work_time_seconds, count_orders_completed) WHERE sum_work_time_seconds IS NOT NULL OR count_orders_completed IS NOT NULL",
            
            "-- 4. Índice en drivers",
            "CREATE INDEX IF NOT EXISTS idx_drivers_park_hire_date_optimized ON drivers(park_id, hire_date DESC, driver_id)",
            
            "-- 5. Índice en contractor_tracking_history",
            "CREATE INDEX IF NOT EXISTS idx_contractor_tracking_driver_calc_desc ON contractor_tracking_history(driver_id, calculation_date DESC)",
            
            "-- 6. Índice en lead_matches",
            "CREATE INDEX IF NOT EXISTS idx_lead_matches_driver_matched_optimized ON lead_matches(driver_id, matched_at DESC) WHERE is_discarded = false",
            
            "-- 7. Índice en scout_registrations",
            "CREATE INDEX IF NOT EXISTS idx_scout_registrations_driver_optimized ON scout_registrations(driver_id, registration_date DESC, scout_id) WHERE is_matched = true AND driver_id IS NOT NULL",
            
            "-- 8. Actualizar estadísticas",
            "ANALYZE summary_daily",
            "ANALYZE drivers",
            "ANALYZE contractor_tracking_history",
            "ANALYZE lead_matches",
            "ANALYZE scout_registrations"
        };
        
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("✓ Conexión exitosa!");
            System.out.println("==========================================");
            
            try (Statement stmt = conn.createStatement()) {
                for (String sql : sqlStatements) {
                    if (sql.trim().startsWith("--")) {
                        System.out.println("\n" + sql);
                        continue;
                    }
                    
                    try {
                        long startTime = System.currentTimeMillis();
                        stmt.execute(sql);
                        long duration = System.currentTimeMillis() - startTime;
                        System.out.println("✓ Ejecutado en " + duration + "ms");
                    } catch (SQLException e) {
                        if (e.getMessage().contains("already exists")) {
                            System.out.println("⚠ Índice ya existe (OK)");
                        } else {
                            System.err.println("✗ Error: " + e.getMessage());
                        }
                    }
                }
                
                System.out.println("\n==========================================");
                System.out.println("Verificando índices creados...");
                System.out.println("==========================================");
                
                String verifySql = "SELECT tablename, indexname FROM pg_indexes " +
                    "WHERE tablename IN ('summary_daily', 'drivers', 'contractor_tracking_history', 'lead_matches', 'scout_registrations') " +
                    "AND (indexname LIKE '%optimized%' OR indexname LIKE '%date_file%') " +
                    "ORDER BY tablename, indexname";
                
                try (ResultSet rs = stmt.executeQuery(verifySql)) {
                    while (rs.next()) {
                        System.out.println("✓ " + rs.getString("tablename") + " -> " + rs.getString("indexname"));
                    }
                }
                
                System.out.println("\n==========================================");
                System.out.println("✓ Optimización completada exitosamente!");
                System.out.println("==========================================");
            }
        } catch (SQLException e) {
            System.err.println("Error de conexión: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

