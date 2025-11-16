package servicios;

import modelos.Articulo;
import modelos.Etiqueta;
import modelos.User;
import app.java.DatabaseUtil;

import javax.persistence.EntityManager;
import java.util.List;

public class ArticuloServicios {

    public boolean agregarArticulo(String titulo, String cuerpo, User autor, List<Etiqueta> etiquetas) {
        if (titulo == null || titulo.trim().isEmpty() || cuerpo == null || cuerpo.trim().isEmpty()) {
            return false;
        }

        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            Articulo nuevoArticulo = new Articulo();
            nuevoArticulo.setTitulo(titulo);
            nuevoArticulo.setCuerpo(cuerpo);
            nuevoArticulo.setAutor(autor);
            nuevoArticulo.setEtiquetas(etiquetas);

            em.persist(nuevoArticulo);
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

    public List<Articulo> listarArticulos() {
        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            return em.createQuery("SELECT a FROM Articulo a", Articulo.class).getResultList();
        } finally {
            em.close();
        }
    }

    public Articulo obtenerPorId(Long id) {
        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            return em.find(Articulo.class, id);
        } finally {
            em.close();
        }
    }

    public boolean actualizarArticulo(Articulo articuloActualizado) {
        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            Articulo articulo = em.find(Articulo.class, articuloActualizado.getId());
            if (articulo != null) {
                articulo.setTitulo(articuloActualizado.getTitulo());
                articulo.setCuerpo(articuloActualizado.getCuerpo());
                em.merge(articulo);
                em.getTransaction().commit();
                return true;
            }
            return false;

        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            return false;
        } finally {
            em.close();
        }
    }

    public boolean eliminarArticulo(Long id) {
        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            Articulo articulo = em.find(Articulo.class, id);
            if (articulo != null) {
                em.remove(articulo);
                em.getTransaction().commit();
                return true;
            }
            return false;

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
