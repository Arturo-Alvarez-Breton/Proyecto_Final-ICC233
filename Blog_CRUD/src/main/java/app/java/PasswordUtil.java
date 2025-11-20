package app.java;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordUtil {

    /**
     * Hashes a plain text password using bcrypt.
     * The salt is automatically generated and embedded in the resulting hash.
     * @param plainPassword The user's plain text password.
     * @return The secure, hashed password string to store in the database.
     */
    public static String hashPassword(String plainPassword) {
        // BCrypt.gensalt() generates a random salt with a default log rounds (cost factor of 10)
        // A cost factor of 12 is a common recommendation for modern applications.
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
    }

    /**
     * Checks a plain text password against a stored hashed password.
     * @param plainPassword The plain text password provided during login.
     * @param hashedPasswordFromDB The hashed password retrieved from the database.
     * @return true if the passwords match, false otherwise.
     */
    public static boolean checkPassword(String plainPassword, String hashedPasswordFromDB) {
        // BCrypt extracts the salt from the stored hash and uses it to hash the provided password for comparison.
        return BCrypt.checkpw(plainPassword, hashedPasswordFromDB);
    }
}
