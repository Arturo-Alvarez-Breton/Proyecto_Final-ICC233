package modelos;

import javax.persistence.*;
import java.util.*;

@Entity
public class Etiqueta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String etiqueta;

    @ManyToMany(mappedBy = "etiquetas")
    private List<Articulo> articulos = new ArrayList<>();

    public Etiqueta() {
    }

    public Etiqueta(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    // Getters y Setters
    public Long getId() {
        return id;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public void setEtiqueta(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public List<Articulo> getArticulos() {
        return articulos;
    }

    public void setArticulos(List<Articulo> articulos) {
        this.articulos = articulos;
    }

    // Métodos helper para la relación bidireccional
    public void addArticulo(Articulo articulo) {
        this.articulos.add(articulo);
        articulo.getEtiquetas().add(this);
    }

    public void removeArticulo(Articulo articulo) {
        this.articulos.remove(articulo);
        articulo.getEtiquetas().remove(this);
    }
}