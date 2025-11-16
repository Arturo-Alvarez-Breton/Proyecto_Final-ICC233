package app.java;

import org.h2.tools.Server;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.sql.SQLException;

public class DatabaseUtil {
    private static EntityManagerFactory emf;
    private static Server h2Server;

    static {
        try {
            // Iniciar servidor H2 con parámetros para creación automática
            h2Server = Server.createTcpServer(
                    "-tcpPort", "9092",
                    "-tcpAllowOthers",
                    "-tcpPassword", "",
                    "-ifNotExists"  // Permite crear la DB si no existe
            ).start();

            System.out.println("H2 server started: " + h2Server.getStatus());

            // Pequeño delay para asegurar inicio del servidor
            Thread.sleep(1000);

            // Crear EntityManagerFactory
            emf = Persistence.createEntityManagerFactory("blogPU");
        } catch (Exception e) {
            if (h2Server != null) h2Server.stop();
            throw new RuntimeException("Error al iniciar H2 Server", e);
        }

        // Registrar shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (emf != null && emf.isOpen()) {
                emf.close();
                System.out.println("EntityManagerFactory closed");
            }
            if (h2Server != null && h2Server.isRunning(true)) {
                h2Server.stop();
                System.out.println("H2 server stopped");
            }
        }));
    }

    public static EntityManager getEntityManager() {
        return emf.createEntityManager();
    }

    // Método para desarrollo: acceder a la consola web H2
    public static void iniciarConsolaWeb() throws SQLException {
        Server webServer = Server.createWebServer("-webPort", "8082", "-ifNotExists").start();
        System.out.println("Consola web H2 disponible en: http://localhost:8082");
    }
}