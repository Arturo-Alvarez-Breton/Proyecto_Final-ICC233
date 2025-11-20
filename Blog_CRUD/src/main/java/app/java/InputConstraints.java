package app.java;

import java.util.Map;
import java.util.HashMap;

public final class InputConstraints {
    private InputConstraints() {}

    // User
    public static final int USERNAME_MAX = 50;
    public static final int NOMBRE_MAX = 100;
    public static final int PASSWORD_HASH_MAX = 128; // DB column length for hashed passwords

    // Files / images
    public static final int FOTO_NOMBRE_MAX = 255;
    public static final int FOTO_MIMETYPE_MAX = 100;

    // Articles
    public static final int TITULO_MAX = 200;
    public static final int ARTICULO_CUERPO_MAX = 10000; // reasonable upper bound for article body

    // Comments and messages
    public static final int COMENTARIO_MAX = 2000;
    public static final int MENSAJE_MAX = 2000;
    public static final int EMISOR_ANONIMO_MAX = 100;

    // Tags
    public static final int ETIQUETA_MAX = 100;

    // Simple helpers
    public static boolean exceeds(String s, int max) {
        if (s == null) return false;
        return s.length() > max;
    }

    public static Map<String, String> validateUserInput(String username, String nombre, String password) {
        Map<String,String> errors = new HashMap<>();
        if (username != null && exceeds(username, USERNAME_MAX)) errors.put("username", "El username supera " + USERNAME_MAX + " caracteres");
        if (nombre != null && exceeds(nombre, NOMBRE_MAX)) errors.put("nombre", "El nombre supera " + NOMBRE_MAX + " caracteres");
        if (password != null && password.length() > 1000) errors.put("password", "La contraseña es demasiado larga");
        return errors;
    }
}

