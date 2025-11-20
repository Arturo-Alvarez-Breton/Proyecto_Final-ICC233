package Controladores;

import app.java.DatabaseUtil;
import app.java.InputConstraints;
import io.javalin.http.Context;
import modelos.Articulo;
import modelos.Comentario;
import modelos.User;

import javax.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class CommentController {

    public static void crearComentario(Context ctx) {
        User usuario = ctx.sessionAttribute("usuario");
        if (usuario == null) {
            ctx.redirect("/login");
            return;
        }

        long articuloId;
        try {
            articuloId = Long.parseLong(Objects.requireNonNull(ctx.formParam("articuloId")));
        } catch (Exception e) {
            ctx.status(400).result("ID de artículo inválido");
            return;
        }

        String comentarioTexto = ctx.formParam("comentario");
        if (comentarioTexto == null || comentarioTexto.trim().isEmpty()) {
            ctx.status(400).result("El comentario no puede estar vacío");
            return;
        }

        // Validate maximum length
        if (InputConstraints.exceeds(comentarioTexto, InputConstraints.COMENTARIO_MAX)) {
            // Load article to render the page with an error message
            EntityManager em = DatabaseUtil.getEntityManager();
            try {
                Articulo articulo = em.find(Articulo.class, articuloId);
                Map<String,Object> model = new HashMap<>();
                model.put("articulo", articulo);
                model.put("usuario", usuario);
                model.put("error", "El comentario supera el máximo de " + InputConstraints.COMENTARIO_MAX + " caracteres");
                ctx.render("articulo.html", model);
            } finally {
                em.close();
            }
            return;
        }

        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            Articulo articulo = em.find(Articulo.class, articuloId);

            Comentario comentario = new Comentario();
            comentario.setComentario(comentarioTexto.trim());
            comentario.setAutor(usuario);
            comentario.setArticulo(articulo);

            em.persist(comentario);
            em.getTransaction().commit();

            ctx.redirect("/articulo/" + articuloId);
        } catch (Exception e) {
            ctx.status(400).result("Error al crear comentario");
        } finally {
            em.close();
        }
    }

    public static void eliminarComentario(Context ctx) {
        User usuario = ctx.sessionAttribute("usuario");
        if (usuario == null) return;

        long comentarioId = Long.parseLong(ctx.pathParam("id"));

        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            Comentario comentario = em.find(Comentario.class, comentarioId);
            // Comparar utilizando el id del usuario
            if (comentario != null &&
                    (usuario.isAdmin() || comentario.getAutor().getId().equals(usuario.getId()))) {
                em.remove(comentario);
            }

            em.getTransaction().commit();
            ctx.redirect("/mis-articulos");
        } finally {
            em.close();
        }
    }

    public static void listarComentarios(Context ctx) {
        try {
            long articuloId = Long.parseLong(ctx.pathParam("articuloId"));

            EntityManager em = DatabaseUtil.getEntityManager();
            List<Comentario> comentarios = em.createQuery(
                            "SELECT c FROM Comentario c WHERE c.articulo.id = :articuloId", Comentario.class)
                    .setParameter("articuloId", articuloId)
                    .getResultList();

            Map<String, Object> response = new HashMap<>();
            response.put("articuloId", articuloId);
            response.put("comentarios", comentarios.stream()
                    .map(c -> {
                        Map<String, Object> comMap = new HashMap<>();
                        comMap.put("id", c.getId());
                        comMap.put("autor", c.getAutor().getNombre());
                        comMap.put("texto", c.getComentario());
                        return comMap;
                    }).collect(Collectors.toList()));

            ctx.json(response);
        } catch (NumberFormatException e) {
            ctx.status(400).json(Map.of("error", "ID inválido"));
        }
    }
}
