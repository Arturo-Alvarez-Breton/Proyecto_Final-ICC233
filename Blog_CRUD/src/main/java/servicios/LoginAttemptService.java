package servicios;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio para prevenir ataques de fuerza bruta en el login.
 * Implementa bloqueo temporal de cuentas tras múltiples intentos fallidos.
 */
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5; // Máximo de intentos permitidos
    private static final int LOCKOUT_DURATION_MINUTES = 15; // Duración del bloqueo en minutos
    private static final int ATTEMPT_RESET_MINUTES = 30; // Tiempo para resetear el contador

    // Almacena intentos fallidos por username
    private static final Map<String, LoginAttemptData> attemptCache = new ConcurrentHashMap<>();

    // Almacena intentos fallidos por IP (protección adicional)
    private static final Map<String, LoginAttemptData> ipAttemptCache = new ConcurrentHashMap<>();

    private static class LoginAttemptData {
        int attempts;
        LocalDateTime lastAttempt;
        LocalDateTime lockoutUntil;

        LoginAttemptData() {
            this.attempts = 0;
            this.lastAttempt = LocalDateTime.now();
            this.lockoutUntil = null;
        }
    }

    /**
     * Registra un intento de login fallido para un usuario y una IP.
     */
    public static void loginFailed(String username, String ipAddress) {
        if (username != null && !username.isEmpty()) {
            registerFailedAttempt(attemptCache, username.toLowerCase());
        }
        if (ipAddress != null && !ipAddress.isEmpty()) {
            registerFailedAttempt(ipAttemptCache, ipAddress);
        }
    }

    /**
     * Registra un intento de login exitoso, limpiando los contadores.
     */
    public static void loginSucceeded(String username, String ipAddress) {
        if (username != null && !username.isEmpty()) {
            attemptCache.remove(username.toLowerCase());
        }
        if (ipAddress != null && !ipAddress.isEmpty()) {
            ipAttemptCache.remove(ipAddress);
        }
    }

    /**
     * Verifica si un usuario está bloqueado.
     * @return true si el usuario está bloqueado, false en caso contrario
     */
    public static boolean isBlocked(String username, String ipAddress) {
        boolean userBlocked = username != null && !username.isEmpty()
            && isKeyBlocked(attemptCache, username.toLowerCase());
        boolean ipBlocked = ipAddress != null && !ipAddress.isEmpty()
            && isKeyBlocked(ipAttemptCache, ipAddress);

        return userBlocked || ipBlocked;
    }

    /**
     * Obtiene el tiempo restante de bloqueo en minutos.
     * @return minutos restantes de bloqueo, o 0 si no está bloqueado
     */
    public static long getRemainingLockoutMinutes(String username, String ipAddress) {
        long userLockout = 0;
        long ipLockout = 0;

        if (username != null && !username.isEmpty()) {
            LoginAttemptData data = attemptCache.get(username.toLowerCase());
            if (data != null && data.lockoutUntil != null) {
                userLockout = ChronoUnit.MINUTES.between(LocalDateTime.now(), data.lockoutUntil);
                if (userLockout < 0) userLockout = 0;
            }
        }

        if (ipAddress != null && !ipAddress.isEmpty()) {
            LoginAttemptData data = ipAttemptCache.get(ipAddress);
            if (data != null && data.lockoutUntil != null) {
                ipLockout = ChronoUnit.MINUTES.between(LocalDateTime.now(), data.lockoutUntil);
                if (ipLockout < 0) ipLockout = 0;
            }
        }

        return Math.max(userLockout, ipLockout);
    }

    /**
     * Obtiene el número de intentos fallidos restantes antes del bloqueo.
     */
    public static int getRemainingAttempts(String username) {
        if (username == null || username.isEmpty()) {
            return MAX_ATTEMPTS;
        }

        LoginAttemptData data = attemptCache.get(username.toLowerCase());
        if (data == null) {
            return MAX_ATTEMPTS;
        }

        // Si el último intento fue hace mucho tiempo, resetear
        if (ChronoUnit.MINUTES.between(data.lastAttempt, LocalDateTime.now()) > ATTEMPT_RESET_MINUTES) {
            attemptCache.remove(username.toLowerCase());
            return MAX_ATTEMPTS;
        }

        return Math.max(0, MAX_ATTEMPTS - data.attempts);
    }

    /**
     * Limpia entradas antiguas del cache (mantenimiento).
     */
    public static void cleanupOldEntries() {
        LocalDateTime now = LocalDateTime.now();

        attemptCache.entrySet().removeIf(entry -> {
            LoginAttemptData data = entry.getValue();
            return ChronoUnit.MINUTES.between(data.lastAttempt, now) > ATTEMPT_RESET_MINUTES
                && (data.lockoutUntil == null || now.isAfter(data.lockoutUntil));
        });

        ipAttemptCache.entrySet().removeIf(entry -> {
            LoginAttemptData data = entry.getValue();
            return ChronoUnit.MINUTES.between(data.lastAttempt, now) > ATTEMPT_RESET_MINUTES
                && (data.lockoutUntil == null || now.isAfter(data.lockoutUntil));
        });
    }

    // Métodos privados auxiliares

    private static void registerFailedAttempt(Map<String, LoginAttemptData> cache, String key) {
        LoginAttemptData data = cache.computeIfAbsent(key, k -> new LoginAttemptData());

        LocalDateTime now = LocalDateTime.now();

        // Si el último intento fue hace mucho tiempo, resetear el contador
        if (ChronoUnit.MINUTES.between(data.lastAttempt, now) > ATTEMPT_RESET_MINUTES) {
            data.attempts = 1;
        } else {
            data.attempts++;
        }

        data.lastAttempt = now;

        // Si se alcanzó el máximo de intentos, aplicar bloqueo
        if (data.attempts >= MAX_ATTEMPTS) {
            data.lockoutUntil = now.plusMinutes(LOCKOUT_DURATION_MINUTES);
        }
    }

    private static boolean isKeyBlocked(Map<String, LoginAttemptData> cache, String key) {
        LoginAttemptData data = cache.get(key);

        if (data == null || data.lockoutUntil == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();

        // Si el bloqueo ya expiró, limpiar y retornar false
        if (now.isAfter(data.lockoutUntil)) {
            cache.remove(key);
            return false;
        }

        return true;
    }

    /**
     * Método para testing: resetear todos los intentos.
     */
    public static void resetAllAttempts() {
        attemptCache.clear();
        ipAttemptCache.clear();
    }
}

