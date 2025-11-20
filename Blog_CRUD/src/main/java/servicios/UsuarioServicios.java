package servicios;

import modelos.User;
import app.java.DatabaseUtil;
import app.java.PasswordUtil;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import java.util.List;

public class UsuarioServicios {

    public static User autenticar(String username, String password) {
        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            User user = em.createQuery(
                            "SELECT u FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", username)
                    .getSingleResult();

            if (user != null && password != null) {
                boolean ok = PasswordUtil.checkPassword(password, user.getPassword());
                if (ok) return user;
            }
            return null;
        } catch (NoResultException e) {
            return null;
        } finally {
            em.close();
        }
    }

    public static List<User> listarUsuarios() {
        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            return em.createQuery("SELECT u FROM User u", User.class).getResultList();
        } finally {
            em.close();
        }
    }

    public static void actualizarUsuario(User usuario) {
        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();
            em.merge(usuario); // Actualizará todos los campos incluyendo la foto
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    // Nuevo método para obtener un usuario a partir del username
    public static User obtenerUsuarioPorUsername(String username) {
        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            return em.createQuery("SELECT u FROM User u WHERE u.username = :username", User.class)
                    .setParameter("username", username)
                    .getSingleResult();
        } catch (Exception e) {
            return null;
        } finally {
            em.close();
        }
    }
}
