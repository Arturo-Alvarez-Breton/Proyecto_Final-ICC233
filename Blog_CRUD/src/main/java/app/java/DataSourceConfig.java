package app.java;

import org.postgresql.ds.PGSimpleDataSource;

public class DataSourceConfig {

    public static PGSimpleDataSource getDataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();

        // Intenta extraer la URL de la variable de entorno JDBC_DATABASE_URL
        String jdbcUrl = System.getenv("JDBC_DATABASE_URL");

        if (jdbcUrl != null && !jdbcUrl.isEmpty()) {
            // Si la variable de entorno está configurada, la usamos
            ds.setUrl(jdbcUrl);
        } else {
            // Si no está configurada, lanzamos una excepción para indicar el error
            throw new RuntimeException("La variable de entorno JDBC_DATABASE_URL no está configurada.");
        }

        return ds;
    }
}
