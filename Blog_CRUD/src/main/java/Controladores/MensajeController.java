package Controladores;

import app.java.DatabaseUtil;
import io.javalin.http.Context;
import modelos.Mensaje;
import modelos.User;

import javax.persistence.EntityManager;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MensajeController {

    // Obtener mensajes: si el usuario no está autenticado se muestran todos los mensajes,
    // y si está autenticado se filtra según el rol (admin o autor ven todos, demás solo sus mensajes)
    public static void obtenerMensajes(Context ctx) {
        User usuario = ctx.sessionAttribute("usuario");
        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            List<Mensaje> mensajes;
            if (usuario == null) {
                // Usuario anónimo: mostrar todos los mensajes
                mensajes = em.createQuery("SELECT m FROM Mensaje m ORDER BY m.fecha DESC", Mensaje.class)
                        .getResultList();
            } else if (usuario.isAdmin() || usuario.isAutor()) {
                mensajes = em.createQuery("SELECT m FROM Mensaje m ORDER BY m.fecha DESC", Mensaje.class)
                        .getResultList();
            } else {
                mensajes = em.createQuery("SELECT m FROM Mensaje m WHERE m.emisor = :usuario OR m.receptor = :usuario ORDER BY m.fecha DESC", Mensaje.class)
                        .setParameter("usuario", usuario)
                        .getResultList();
            }

            List<Map<String, Object>> mensajesMap = mensajes.stream().map(m -> {
                Map<String, Object> mensajeMap = new HashMap<>();
                mensajeMap.put("id", m.getId());
                // Si el emisor no es nulo se utiliza su username o nombre; de lo contrario, se usa el campo emisorAnonimo.
                String autor;
                if (m.getEmisor() != null) {
                    autor = m.getEmisor().getUsername() != null ? m.getEmisor().getUsername() : m.getEmisor().getNombre();
                } else {
                    autor = m.getEmisorAnonimo();
                }
                mensajeMap.put("autor", autor);
                mensajeMap.put("contenido", m.getContenido());
                mensajeMap.put("fecha", m.getFecha());
                return mensajeMap;
            }).collect(Collectors.toList());

            ctx.json(Map.of("mensajes", mensajesMap));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error al obtener los mensajes"));
        } finally {
            em.close();
        }
    }

    // Enviar mensaje: si el usuario no está autenticado, se usa el parámetro "nombre"
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
            if (usuario == null) {
                // Si no hay usuario en sesión, se usa el parámetro "nombre" o se asigna "Anónimo"
                String nombre = ctx.formParam("nombre");
                if (nombre == null || nombre.trim().isEmpty()) {
                    nombre = "Anónimo";
                }
                usuario = new User();
                usuario.setNombre(nombre);
                // Nota: Este usuario es temporal y no se persiste en la BD
            }

            Mensaje mensaje = new Mensaje();
            mensaje.setEmisor(usuario);
            mensaje.setContenido(contenido.trim());
            mensaje.setFecha(new Date(System.currentTimeMillis()));

            // Si se envía un receptor (para chats uno a uno) se podría procesar aquí:
            String receptorParam = ctx.formParam("receptor");
            if (receptorParam != null && !receptorParam.trim().isEmpty()) {
                // Implementar la lógica para obtener el usuario receptor (por ejemplo, a partir de su alias)
                // User receptor = UserService.obtenerPorAlias(receptorParam);
                // mensaje.setReceptor(receptor);
            }

            em.persist(mensaje);
            em.getTransaction().commit();

            ctx.json(Map.of("mensaje", "Mensaje enviado correctamente"));
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            ctx.status(500).json(Map.of("error", "Error al enviar el mensaje"));
        } finally {
            em.close();
        }
    }

    // Eliminar mensaje: solo se permite si el usuario está autenticado y es el emisor o es admin
    public static void eliminarMensaje(Context ctx) {
        User usuario = ctx.sessionAttribute("usuario");
        if (usuario == null) {
            ctx.status(401).json(Map.of("error", "Usuario no autenticado"));
            return;
        }

        long mensajeId;
        try {
            mensajeId = Long.parseLong(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            ctx.status(400).json(Map.of("error", "ID de mensaje inválido"));
            return;
        }

        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();
            Mensaje mensaje = em.find(Mensaje.class, mensajeId);
            if (mensaje == null) {
                ctx.status(404).json(Map.of("error", "Mensaje no encontrado"));
                return;
            }

            if (!mensaje.getEmisor().getId().equals(usuario.getId()) && !usuario.isAdmin()) {
                ctx.status(403).json(Map.of("error", "No tienes permisos para eliminar este mensaje"));
                return;
            }

            em.remove(mensaje);
            em.getTransaction().commit();
            ctx.json(Map.of("mensaje", "Mensaje eliminado correctamente"));
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            ctx.status(500).json(Map.of("error", "Error al eliminar el mensaje"));
        } finally {
            em.close();
        }
    }

    // Obtener historial de mensajes para la administración de chats:
    // Se muestran todos los mensajes en los que el usuario indicado (por alias) haya sido emisor o receptor.
    public static void obtenerMensajesAdmin(Context ctx) {
        // Se espera que el alias del usuario se reciba como parámetro en la URL, por ejemplo: /api/chats/{username}
        String alias = ctx.pathParam("username");
        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            List<Mensaje> mensajes = em.createQuery(
                            "SELECT m FROM Mensaje m " +
                                    "WHERE (m.emisor IS NOT NULL AND m.emisor.username = :alias) " +
                                    "   OR (m.receptor IS NOT NULL AND m.receptor.username = :alias) " +
                                    "ORDER BY m.fecha ASC", Mensaje.class)
                    .setParameter("alias", alias)
                    .getResultList();

            List<Map<String, Object>> mensajesMap = mensajes.stream().map(m -> {
                Map<String, Object> mensajeMap = new HashMap<>();
                mensajeMap.put("id", m.getId());
                String autor;
                if (m.getEmisor() != null) {
                    autor = m.getEmisor().getUsername() != null ? m.getEmisor().getUsername() : m.getEmisor().getNombre();
                } else {
                    autor = m.getEmisorAnonimo();
                }
                mensajeMap.put("autor", autor);
                mensajeMap.put("contenido", m.getContenido());
                mensajeMap.put("fecha", m.getFecha());
                return mensajeMap;
            }).collect(Collectors.toList());

            ctx.json(Map.of("mensajes", mensajesMap));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("error", "Error al obtener el historial del chat"));
        } finally {
            em.close();
        }
    }
}
