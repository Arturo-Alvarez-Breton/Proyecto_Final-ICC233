package app.java;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.HashMap;
import java.util.Map;

public class DatabaseUtil {
    private static EntityManagerFactory emf;

    static {
        try {
            // Load JDBC properties from .env via DataSourceConfig and override persistence.xml values
            Map<String, String> props = new HashMap<>();
            String url = DataSourceConfig.getDbUrl();
            String user = DataSourceConfig.getDbUser();
            String password = DataSourceConfig.getDbPassword();

            if (url != null && !url.isEmpty()) props.put("javax.persistence.jdbc.url", url);
            if (user != null) props.put("javax.persistence.jdbc.user", user);
            if (password != null) props.put("javax.persistence.jdbc.password", password);
            // Ensure driver is set
            props.put("javax.persistence.jdbc.driver", "com.microsoft.sqlserver.jdbc.SQLServerDriver");

            emf = Persistence.createEntityManagerFactory("blogPU", props);
            System.out.println("Conexión a SQL Server establecida correctamente");
        } catch (Exception e) {
            throw new RuntimeException("Error al conectar con SQL Server: " + e.getMessage(), e);
        }

        // Registrar shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (emf != null && emf.isOpen()) {
                emf.close();
                System.out.println("EntityManagerFactory closed");
            }
        }));
    }

    public static EntityManager getEntityManager() {
        return emf.createEntityManager();
    }
}