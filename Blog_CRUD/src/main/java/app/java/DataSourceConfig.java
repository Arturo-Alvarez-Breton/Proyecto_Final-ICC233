package app.java;

import io.github.cdimascio.dotenv.Dotenv;

public class DataSourceConfig {
    private static Dotenv dotenv;

    static {
        try {
            dotenv = Dotenv.configure()
                    .directory("./")
                    .ignoreIfMissing()
                    .load();
        } catch (Exception e) {
            System.err.println("Error al cargar el archivo .env: " + e.getMessage());
        }
    }

    public static String get(String key) {
        if (dotenv != null) {
            return dotenv.get(key);
        }
        return System.getenv(key);
    }

    public static String getDbUrl() {
        String server = get("DB_SERVER");
        String dbName = get("DB_NAME");
        return String.format("jdbc:sqlserver://%s:1433;databaseName=%s;trustServerCertificate=true;encrypt=true;",
                            server, dbName);
    }

    public static String getDbUser() {
        return get("DB_USER");
    }

    public static String getDbPassword() {
        return get("DB_PASSWORD");
    }
}
