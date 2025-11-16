package servicios;

import modelos.Comentario;
import modelos.Articulo;
import modelos.User;
import app.java.DatabaseUtil;

import javax.persistence.EntityManager;

public class ComentarioServicios {

    public static Comentario crearComentario(String contenido, User autor, Articulo articulo) {
        if (contenido == null || contenido.trim().isEmpty() || autor == null || articulo == null) {
            return null;
        }

        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            Comentario nuevoComentario = new Comentario();
            nuevoComentario.setComentario(contenido.trim());
            nuevoComentario.setAutor(autor);
            nuevoComentario.setArticulo(articulo);

            em.persist(nuevoComentario);

            // Actualizar relación bidireccional
            articulo.getComentarios().add(nuevoComentario);
            em.merge(articulo);

            em.getTransaction().commit();
            return nuevoComentario;

        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            return null;
        } finally {
            em.close();
        }
    }

    public static boolean eliminarComentario(Long comentarioId, User usuario) {
        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            Comentario comentario = em.find(Comentario.class, comentarioId);
            if (comentario == null) return false;

            // Se compara el id del autor para verificar la propiedad
            boolean esAutor = comentario.getAutor().getId().equals(usuario.getId());
            if (!usuario.isAdmin() && !esAutor) return false;

            // Actualizar relación bidireccional
            Articulo articulo = comentario.getArticulo();
            articulo.getComentarios().remove(comentario);
            em.merge(articulo);

            em.remove(comentario);
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
