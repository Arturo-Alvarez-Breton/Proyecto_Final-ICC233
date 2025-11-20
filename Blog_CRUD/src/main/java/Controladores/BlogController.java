package Controladores;

import app.java.DatabaseUtil;
import app.java.InputConstraints;
import app.java.InputSanitizer;
import io.javalin.http.Context;
import modelos.Articulo;
import modelos.Etiqueta;
import modelos.User;
import servicios.ArticuloServicios;

import javax.persistence.EntityManager;
import java.util.*;

public class BlogController {

    public static void mostrarFormularioCrear(Context ctx) {
        User usuario = ctx.sessionAttribute("usuario");
        if (usuario == null || !usuario.isAutor()) {
            ctx.redirect("/login");
            return;
        }
        ctx.render("mis-articulos.html");
    }

    public static void crearArticulo(Context ctx) {
        User usuario = ctx.sessionAttribute("usuario");
        if (usuario == null || !usuario.isAutor()) {
            ctx.redirect("/login");
            return;
        }

        String titulo = ctx.formParam("titulo");
        String cuerpo = ctx.formParam("contenido");
        String etiquetasStr = ctx.formParam("etiquetas");

        // Sanitize inputs
        titulo = InputSanitizer.stripTags(titulo);
        cuerpo = InputSanitizer.stripTags(cuerpo);

        // Validate lengths
        if (titulo == null || titulo.trim().isEmpty() || InputConstraints.exceeds(titulo, InputConstraints.TITULO_MAX)) {
            renderMisArticulosPage(ctx, usuario, "Título inválido o demasiado largo (máx " + InputConstraints.TITULO_MAX + " caracteres)");
            return;
        }
        if (cuerpo == null || cuerpo.trim().isEmpty() || InputConstraints.exceeds(cuerpo, InputConstraints.ARTICULO_CUERPO_MAX)) {
            renderMisArticulosPage(ctx, usuario, "Contenido inválido o demasiado largo (máx " + InputConstraints.ARTICULO_CUERPO_MAX + " caracteres)");
            return;
        }

        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            Articulo articulo = new Articulo();
            articulo.setTitulo(titulo.trim());
            articulo.setCuerpo(cuerpo.trim());
            articulo.setAutor(usuario);
            articulo.setFecha(new Date());

            if (etiquetasStr != null && !etiquetasStr.isEmpty()) {
                // sanitize and process tags
                Set<Etiqueta> etiquetas = procesarEtiquetas(em, etiquetasStr);
                articulo.setEtiquetas(new ArrayList<>(etiquetas));
            }

            em.persist(articulo);
            em.getTransaction().commit();

            ctx.redirect("/mis-articulos");
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            renderMisArticulosPage(ctx, usuario, "Error al crear artículo");
        } finally {
            em.close();
        }
    }

    private static Set<Etiqueta> procesarEtiquetas(EntityManager em, String etiquetasStr) {
        Set<Etiqueta> etiquetas = new HashSet<>();
        for (String nombre : etiquetasStr.split(",")) {
            nombre = nombre.trim();
            if (!nombre.isEmpty()) {
                // sanitize tag
                nombre = InputSanitizer.stripTags(nombre);
                if (InputConstraints.exceeds(nombre, InputConstraints.ETIQUETA_MAX)) {
                    // skip overly long tags
                    continue;
                }
                String finalNombre = nombre;
                Etiqueta etiqueta = em.createQuery(
                                "SELECT e FROM Etiqueta e WHERE e.etiqueta = :nombre", Etiqueta.class)
                        .setParameter("nombre", nombre)
                        .getResultStream()
                        .findFirst()
                        .orElseGet(() -> {
                            Etiqueta nueva = new Etiqueta();
                            nueva.setEtiqueta(finalNombre);
                            em.persist(nueva);
                            return nueva;
                        });
                etiquetas.add(etiqueta);
            }
        }
        return etiquetas;
    }

    public static void eliminarArticulo(Context ctx) {
        User usuario = ctx.sessionAttribute("usuario");
        if (usuario == null) {
            ctx.redirect("/login");
            return;
        }

        long id = Long.parseLong(ctx.pathParam("id"));

        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            Articulo articulo = em.find(Articulo.class, id);
            if (articulo != null &&
                    (usuario.isAdmin() || articulo.getAutor().getId().equals(usuario.getId()))) {

                // Eliminar relaciones con etiquetas primero
                List<Etiqueta> etiquetas = new ArrayList<>(articulo.getEtiquetas());
                for (Etiqueta etiqueta : etiquetas) {
                    articulo.removeEtiqueta(etiqueta);
                    if (etiqueta.getArticulos().isEmpty()) {
                        em.remove(etiqueta); // Eliminar etiqueta huérfana
                    }
                }

                em.remove(articulo);
            }

            em.getTransaction().commit();
            ctx.redirect("/mis-articulos");
        } finally {
            em.close();
        }
    }

    public static void actualizarArticulo(Context ctx) {
        User usuario = ctx.sessionAttribute("usuario");
        if (usuario == null) {
            ctx.redirect("/login");
            return;
        }

        long id = Long.parseLong(ctx.pathParam("id"));
        String titulo = ctx.formParam("titulo");
        String cuerpo = ctx.formParam("contenido");

        // Sanitize inputs
        titulo = InputSanitizer.stripTags(titulo);
        cuerpo = InputSanitizer.stripTags(cuerpo);

        // Validate lengths
        if (titulo == null || titulo.trim().isEmpty() || InputConstraints.exceeds(titulo, InputConstraints.TITULO_MAX)) {
            renderMisArticulosPage(ctx, usuario, "Título inválido o demasiado largo (máx " + InputConstraints.TITULO_MAX + " caracteres)");
            return;
        }
        if (cuerpo == null || cuerpo.trim().isEmpty() || InputConstraints.exceeds(cuerpo, InputConstraints.ARTICULO_CUERPO_MAX)) {
            renderMisArticulosPage(ctx, usuario, "Contenido inválido o demasiado largo (máx " + InputConstraints.ARTICULO_CUERPO_MAX + " caracteres)");
            return;
        }

        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            Articulo articulo = em.find(Articulo.class, id);
            // Comparar el id del autor para verificar permisos
            if (articulo != null &&
                    (usuario.isAdmin() || articulo.getAutor().getId().equals(usuario.getId()))) {

                articulo.setTitulo(titulo.trim());
                articulo.setCuerpo(cuerpo.trim());
                em.merge(articulo);
            }

            em.getTransaction().commit();
            ctx.redirect("/mis-articulos");
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            renderMisArticulosPage(ctx, usuario, "Error al actualizar artículo");
        } finally {
            em.close();
        }
    }

    public static void mostrarFormularioEditar(Context ctx) {
        User usuario = ctx.sessionAttribute("usuario");
        if (usuario == null) {
            ctx.redirect("/login");
            return;
        }

        long id = Long.parseLong(ctx.pathParam("id"));

        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            Articulo articulo = em.find(Articulo.class, id);
            // Comparar utilizando id
            if (articulo != null &&
                    (usuario.isAdmin() || articulo.getAutor().getId().equals(usuario.getId()))) {

                ctx.render("editar-mis-articulos.html", Map.of(
                        "articulo", articulo,
                        "usuario", usuario
                ));
            } else {
                ctx.redirect("/mis-articulos");
            }
        } finally {
            em.close();
        }
    }

    // Helper to render mis-articulos with articles + usuario + error message
    private static void renderMisArticulosPage(Context ctx, User usuario, String errorMessage) {
        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            List<Articulo> articulos;
            if (usuario != null && usuario.isAdmin()) {
                articulos = em.createQuery("SELECT a FROM Articulo a ORDER BY a.fecha DESC", Articulo.class)
                        .getResultList();
            } else if (usuario != null) {
                articulos = em.createQuery("SELECT a FROM Articulo a WHERE a.autor.id = :id ORDER BY a.fecha DESC", Articulo.class)
                        .setParameter("id", usuario.getId())
                        .getResultList();
            } else {
                articulos = List.of();
            }

            Map<String, Object> model = new HashMap<>();
            model.put("articulos", articulos);
            model.put("usuario", usuario);
            model.put("error", errorMessage);
            ctx.render("mis-articulos.html", model);
        } finally {
            em.close();
        }
    }
}
