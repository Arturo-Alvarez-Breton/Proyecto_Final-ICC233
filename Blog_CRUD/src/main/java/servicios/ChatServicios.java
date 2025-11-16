package servicios;

import modelos.Mensaje;
import modelos.User;
import app.java.DatabaseUtil;


import javax.persistence.EntityManager;
import java.util.Date;
import java.util.List;
import java.util.Map;
import io.javalin.http.Context;


public class ChatServicios {

    public static void enviarMensaje(Context ctx) {
        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            String contenido = ctx.formParam("contenido");
            if (contenido == null || contenido.trim().isEmpty()) {
                ctx.status(400).json(Map.of("error", "El contenido del mensaje no puede estar vacío"));
                return;
            }

            em.getTransaction().begin();

            User usuario = ctx.sessionAttribute("usuario");
            Mensaje mensaje = new Mensaje();
            mensaje.setContenido(contenido.trim());
            mensaje.setFecha(new Date(System.currentTimeMillis()));

            if (usuario == null) {
                // Si no hay usuario en sesión, usar el parámetro "nombre" o asignar "Anónimo"
                String nombre = ctx.formParam("nombre");
                if (nombre == null || nombre.trim().isEmpty()) {
                    nombre = "Anónimo";
                }
                mensaje.setEmisorAnonimo(nombre);
            } else {
                mensaje.setEmisor(usuario);
            }

            em.persist(mensaje);
            em.getTransaction().commit();

            ctx.json(Map.of("mensaje", "Mensaje enviado correctamente"));
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            e.printStackTrace();
            ctx.status(500).json(Map.of("error", "Error al enviar el mensaje"));
        } finally {
            em.close();
        }
    }



    public static List<Mensaje> obtenerMensajes(User usuario) {
        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            if (usuario.isAdmin() || usuario.isAutor()) { // Corrección en la validación de roles
                // Administradores y Autores ven todos los mensajes
                return em.createQuery("SELECT m FROM Mensaje m ORDER BY m.fecha DESC", Mensaje.class)
                        .getResultList();
            } else {
                // Usuarios normales solo ven sus mensajes enviados
                return em.createQuery("SELECT m FROM Mensaje m WHERE m.emisor = :usuario ORDER BY m.fecha DESC", Mensaje.class)
                        .setParameter("usuario", usuario)
                        .getResultList();
            }
        } finally {
            em.close();
        }
    }

    public static boolean eliminarMensaje(Long mensajeId, User usuario) {
        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            Mensaje mensaje = em.find(Mensaje.class, mensajeId);
            if (mensaje == null) {
                em.getTransaction().rollback();
                return false;
            }

            // Verificar que el usuario sea el emisor o un admin
            if (!mensaje.getEmisor().getId().equals(usuario.getId()) && !usuario.isAdmin()) {
                em.getTransaction().rollback();
                return false;
            }

            em.remove(mensaje);
            em.getTransaction().commit();
            return true;

        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            return false;
        } finally {
            em.close();
        }
    }
}
