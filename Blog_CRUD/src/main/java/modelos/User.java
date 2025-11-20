package modelos;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import app.java.PasswordUtil;

@Entity
@Table(name = "usuarios")
public class User {
    // Nueva clave primaria interna
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // El username sigue siendo único
    @Column(unique = true, nullable = false)
    private String username;

    // Store hashed password (bcrypt)
    private String password;

    private String nombre;
    private boolean admin;
    private boolean autor;

    @OneToMany(mappedBy = "autor", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Articulo> articulos = new ArrayList<>();

    @OneToMany(mappedBy = "autor", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comentario> comentarios = new ArrayList<>();

    // campos para la foto
    @Column(nullable = true)
    private String fotoNombre;

    @Column(nullable = true)
    private String fotoMimeType;

    @Lob
    @Column(nullable = true)
    private String fotoBase64;

    public User() {
    }

    public User(String username, String nombre, String password, boolean admin, boolean autor) {
        setUsername(username);
        setPassword(password);
        this.nombre = nombre;
        this.admin = admin;
        this.autor = autor;
    }

    // Getters y Setters

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Stores the bcrypt hash of the provided plain password.
     * Do NOT store plain passwords.
     */
    public void setPassword(String password) {
        if (password != null) {
            this.password = PasswordUtil.hashPassword(password);
        } else {
            this.password = null;
        }
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public boolean isAutor() {
        return autor;
    }

    public void setAutor(boolean autor) {
        this.autor = autor;
    }

    public String getFotoNombre() {
        return fotoNombre;
    }

    public void setFotoNombre(String fotoNombre) {
        this.fotoNombre = fotoNombre;
    }

    public String getFotoMimeType() {
        return fotoMimeType;
    }

    public void setFotoMimeType(String fotoMimeType) {
        this.fotoMimeType = fotoMimeType;
    }

    public String getFotoBase64() {
        return fotoBase64;
    }

    public void setFotoBase64(String fotoBase64) {
        this.fotoBase64 = fotoBase64;
    }
}
