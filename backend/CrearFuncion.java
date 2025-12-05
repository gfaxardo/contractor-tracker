import java.sql.*;

public class CrearFuncion {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://168.119.226.236:5432/yego_integral";
        String user = "yego_user";
        String password = "37>MNA&-35+";
        
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Conectado!");
            
            String[] sqls = {
                "CREATE OR REPLACE FUNCTION to_date_immutable(text) RETURNS date AS $$ SELECT to_date($1, 'DD-MM-YYYY') $$ LANGUAGE sql IMMUTABLE STRICT",
                "CREATE INDEX IF NOT EXISTS idx_summary_daily_date_file_date ON summary_daily(to_date_immutable(date_file))",
                "CREATE INDEX IF NOT EXISTS idx_summary_daily_driver_date_file ON summary_daily(driver_id, to_date_immutable(date_file))",
                "CREATE INDEX IF NOT EXISTS idx_summary_daily_driver_date_metrics ON summary_daily(driver_id, to_date_immutable(date_file), sum_work_time_seconds, count_orders_completed) WHERE sum_work_time_seconds IS NOT NULL OR count_orders_completed IS NOT NULL"
            };
            
            try (Statement stmt = conn.createStatement()) {
                for (String sql : sqls) {
                    try {
                        stmt.execute(sql);
                        System.out.println("OK: " + sql.substring(0, Math.min(60, sql.length())) + "...");
                    } catch (SQLException e) {
                        System.out.println("Error: " + e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

