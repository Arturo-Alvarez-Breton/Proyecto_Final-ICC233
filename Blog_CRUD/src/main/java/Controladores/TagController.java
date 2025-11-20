package Controladores;

import app.java.DatabaseUtil;
import app.java.InputConstraints;
import io.javalin.http.Context;
import modelos.Articulo;
import modelos.Etiqueta;
import modelos.User;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Objects;

public class TagController {

    public static void listarEtiquetas(Context ctx) {
        long articuloId = Long.parseLong(ctx.pathParam("articuloId"));

        EntityManager em = DatabaseUtil.getEntityManager();
        Articulo articulo = em.find(Articulo.class, articuloId);

        if (articulo != null) {
            ctx.json(articulo.getEtiquetas());
        } else {
            ctx.status(404);
        }
    }

    public static void agregarEtiqueta(Context ctx) {
        User usuario = ctx.sessionAttribute("usuario");
        if (usuario == null) return;

        long articuloId = Long.parseLong(Objects.requireNonNull(ctx.formParam("articuloId")));
        String nombre = Objects.requireNonNull(ctx.formParam("etiqueta")).trim();

        // Validate tag length
        if (InputConstraints.exceeds(nombre, InputConstraints.ETIQUETA_MAX)) {
            ctx.status(400).result("La etiqueta supera el máximo de " + InputConstraints.ETIQUETA_MAX + " caracteres");
            return;
        }

        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            Articulo articulo = em.find(Articulo.class, articuloId);
            Etiqueta etiqueta = obtenerOCrearEtiqueta(em, nombre);

            // Use addEtiqueta instead of directly adding to the list
            articulo.addEtiqueta(etiqueta);
            em.merge(articulo);

            em.getTransaction().commit();
            ctx.status(201).result("Etiqueta agregada");
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            ctx.status(500).result("Error al agregar la etiqueta");
        } finally {
            em.close();
        }
    }

    private static Etiqueta obtenerOCrearEtiqueta(EntityManager em, String nombre) {
        List<Etiqueta> resultados = em.createQuery(
                        "SELECT e FROM Etiqueta e WHERE e.etiqueta = :nombre", Etiqueta.class)
                .setParameter("nombre", nombre)
                .getResultList();

        if (resultados.isEmpty()) {
            Etiqueta nueva = new Etiqueta();
            nueva.setEtiqueta(nombre);
            em.persist(nueva);
            return nueva;
        }
        return resultados.get(0);
    }

    public static void eliminarEtiqueta(Context ctx) {
        User usuario = ctx.sessionAttribute("usuario");
        if (usuario == null) return;

        long articuloId = Long.parseLong(Objects.requireNonNull(ctx.formParam("articuloId")));
        long etiquetaId = Long.parseLong(ctx.pathParam("id"));

        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            Articulo articulo = em.find(Articulo.class, articuloId);
            Etiqueta etiqueta = em.find(Etiqueta.class, etiquetaId);

            if (articulo != null && etiqueta != null) {
                articulo.removeEtiqueta(etiqueta);

                // Eliminar etiqueta si no tiene más artículos
                if (etiqueta.getArticulos().isEmpty()) {
                    em.remove(etiqueta);
                }

                em.merge(articulo);
            }

            em.getTransaction().commit();
            ctx.redirect("/mis-articulos");
        } finally {
            em.close();
        }
    }
}