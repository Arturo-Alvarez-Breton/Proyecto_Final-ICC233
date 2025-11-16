package modelos;

import javax.persistence.*;
import java.util.Date;

@Entity
public class Mensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Contenido del mensaje (se usa @Lob para mensajes extensos)
    @Lob
    @Column(nullable = false)
    private String contenido;

    // Usuario autenticado que envía el mensaje. Si el mensaje es de un usuario no autenticado, este campo puede ser null.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emisor_id")
    private User emisor;

    // Campo para identificar al emisor cuando el usuario no está autenticado.
    // Se puede asignar un identificador único (por ejemplo, un UUID o un código generado en el cliente)
    @Column(name = "emisor_anonimo")
    private String emisorAnonimo;

    // Usuario receptor, para asociar el mensaje a una conversación en particular
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receptor_id")
    private User receptor;

    // Fecha en la que se envió el mensaje
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false)
    private Date fecha;

    // Constructor que inicializa la fecha del mensaje
    public Mensaje() {
        this.fecha = new Date();
    }

    // Getters y setters

    public Long getId() {
        return id;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }

    public User getEmisor() {
        return emisor;
    }

    public void setEmisor(User emisor) {
        this.emisor = emisor;
    }

    public String getEmisorAnonimo() {
        return emisorAnonimo;
    }

    public void setEmisorAnonimo(String emisorAnonimo) {
        this.emisorAnonimo = emisorAnonimo;
    }

    public User getReceptor() {
        return receptor;
    }

    public void setReceptor(User receptor) {
        this.receptor = receptor;
    }

    public Date getFecha() {
        return fecha;
    }

    public void setFecha(Date fecha) {
        this.fecha = fecha;
    }


    /**
     * Determina si un usuario puede leer el mensaje en la ventana de admin chats.
     * Solo los administradores y autores tienen permiso para ver el contenido.
     *
     * @param usuario Usuario que intenta leer el mensaje.
     * @return true si el usuario es administrador o autor, false en caso contrario.
     */
    public boolean puedeLeerEnAdminChats(User usuario) {
        if (usuario == null) {
            return false;
        }
        return usuario.isAdmin() || usuario.isAutor();
    }
}
