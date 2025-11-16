package Controladores;

import app.java.DatabaseUtil;
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

        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            Articulo articulo = new Articulo();
            articulo.setTitulo(ctx.formParam("titulo"));
            articulo.setCuerpo(ctx.formParam("contenido"));
            articulo.setAutor(usuario);
            articulo.setFecha(new Date());

            String etiquetasStr = ctx.formParam("etiquetas");
            if (etiquetasStr != null && !etiquetasStr.isEmpty()) {
                Set<Etiqueta> etiquetas = procesarEtiquetas(em, etiquetasStr);
                articulo.setEtiquetas(new ArrayList<>(etiquetas));
            }

            em.persist(articulo);
            em.getTransaction().commit();

            ctx.redirect("/mis-articulos");
        } catch (Exception e) {
            ctx.attribute("error", "Error al crear artículo");
            ctx.render("mis-articulos.html");
        } finally {
            em.close();
        }
    }

    private static Set<Etiqueta> procesarEtiquetas(EntityManager em, String etiquetasStr) {
        Set<Etiqueta> etiquetas = new HashSet<>();
        for (String nombre : etiquetasStr.split(",")) {
            nombre = nombre.trim();
            if (!nombre.isEmpty()) {
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

        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            Articulo articulo = em.find(Articulo.class, id);
            // Comparar el id del autor para verificar permisos
            if (articulo != null &&
                    (usuario.isAdmin() || articulo.getAutor().getId().equals(usuario.getId()))) {

                articulo.setTitulo(ctx.formParam("titulo"));
                articulo.setCuerpo(ctx.formParam("contenido"));
                em.merge(articulo);
            }

            em.getTransaction().commit();
            ctx.redirect("/mis-articulos");
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
}
