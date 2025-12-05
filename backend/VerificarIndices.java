import java.sql.*;

public class VerificarIndices {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://168.119.226.236:5432/yego_integral";
        String user = "yego_user";
        String password = "37>MNA&-35+";
        
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("==========================================");
            System.out.println("VERIFICACIÓN DE ÍNDICES Y FUNCIÓN");
            System.out.println("==========================================\n");
            
            // Verificar función
            try (ResultSet rs = conn.createStatement().executeQuery(
                "SELECT proname FROM pg_proc WHERE proname = 'to_date_immutable'")) {
                if (rs.next()) {
                    System.out.println("✓ Función to_date_immutable: EXISTE");
                } else {
                    System.out.println("✗ Función to_date_immutable: NO EXISTE");
                }
            }
            
            System.out.println("\nÍndices críticos en summary_daily:");
            try (ResultSet rs = conn.createStatement().executeQuery(
                "SELECT indexname FROM pg_indexes " +
                "WHERE tablename = 'summary_daily' " +
                "AND (indexname LIKE '%date_file%' OR indexname LIKE '%optimized%') " +
                "ORDER BY indexname")) {
                int count = 0;
                while (rs.next()) {
                    System.out.println("  ✓ " + rs.getString("indexname"));
                    count++;
                }
                if (count == 0) {
                    System.out.println("  ✗ No se encontraron índices");
                }
            }
            
            System.out.println("\nOtros índices optimizados:");
            try (ResultSet rs = conn.createStatement().executeQuery(
                "SELECT tablename, indexname FROM pg_indexes " +
                "WHERE tablename IN ('drivers', 'contractor_tracking_history', 'lead_matches', 'scout_registrations') " +
                "AND indexname LIKE '%optimized%' " +
                "ORDER BY tablename, indexname")) {
                int count = 0;
                while (rs.next()) {
                    System.out.println("  ✓ " + rs.getString("tablename") + " -> " + rs.getString("indexname"));
                    count++;
                }
                if (count == 0) {
                    System.out.println("  ✗ No se encontraron índices");
                }
            }
            
            System.out.println("\n==========================================");
            System.out.println("✓ Verificación completada");
            System.out.println("==========================================");
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

