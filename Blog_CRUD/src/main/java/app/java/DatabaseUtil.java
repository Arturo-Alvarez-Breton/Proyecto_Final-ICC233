package app.java;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class DatabaseUtil {
    private static EntityManagerFactory emf;

    static {
        try {
            // Crear EntityManagerFactory con la configuración de SQL Server desde persistence.xml
            emf = Persistence.createEntityManagerFactory("blogPU");
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