package app.java;

import Controladores.*;
import modelos.Articulo;
import modelos.Etiqueta;
import modelos.Mensaje;
import modelos.User;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import io.javalin.rendering.template.JavalinThymeleaf;
import io.javalin.websocket.WsContext;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.hibernate5.Hibernate5Module;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class App {

    // Conjunto para almacenar las sesiones activas del WebSocket
    private static final Set<WsContext> wsSessions = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        inicializarBaseDeDatos();
        TemplateEngine templateEngine = configurarThymeleaf();
        Javalin app = iniciarJavalin(templateEngine);
        configurarRutas(app); // Se incluyen todas las rutas, incluidas las de API
        configurarWebSocket(app);
    }

    private static void inicializarBaseDeDatos() {
        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();
            Long count = em.createQuery("SELECT COUNT(u) FROM User u", Long.class).getSingleResult();
            if (count == 0) {
                User admin = new User("admin", "Admin", "admin", true, true);
                em.persist(admin);

                Articulo articulo = new Articulo();
                articulo.setTitulo("Primer Artículo");
                articulo.setCuerpo("Contenido de ejemplo");
                articulo.setAutor(admin);
                articulo.setFecha(new Date());
                em.persist(articulo);
            }
            em.getTransaction().commit();
        } catch (Exception e) {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
            throw e;
        } finally {
            em.close();
        }
        System.out.println("Base de datos SQL Server inicializada correctamente");
    }

    private static TemplateEngine configurarThymeleaf() {
        TemplateEngine templateEngine = new TemplateEngine();
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("/templates/");
        templateResolver.setSuffix(".html");
        templateEngine.setTemplateResolver(templateResolver);
        return templateEngine;
    }

    private static Javalin iniciarJavalin(TemplateEngine templateEngine) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new Hibernate5Module());
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        return Javalin.create(config -> {
            config.staticFiles.add("/static");
            config.fileRenderer(new JavalinThymeleaf(templateEngine));
            config.jsonMapper(new JavalinJackson(mapper, false));
        }).start(7000);
    }

    private static void configurarRutasMensajes(Javalin app) {
        app.get("/mensajes/obtener", MensajeController::obtenerMensajes);
        app.post("/mensajes/enviar", MensajeController::enviarMensaje);
        app.post("/mensajes/{id}/eliminar", MensajeController::eliminarMensaje);
    }

    // Metodo para registrar login (por si no se configura JDBC_DATABASE_URL)
    private static void registrarLogin(String username) {
        String dbUrl = System.getenv("JDBC_DATABASE_URL");
        if (dbUrl == null || dbUrl.isEmpty()) {
//            dbUrl = "jdbc:h2:tcp://localhost//tmp/blogdb;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";
            System.err.println("Advertencia: La variable JDBC_DATABASE_URL no está configurada");
        }
        String sql = "INSERT INTO login_audits (username, login_time) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setTimestamp(2, Timestamp.from(Instant.now()));
            stmt.executeUpdate();
            System.out.println("Login registrado para: " + username);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void configurarRutas(Javalin app) {
        configurarMiddleware(app);
        configurarRutasAutenticacion(app);
        configurarRutasPublicas(app);
        configurarRutasPrivadas(app);
        configurarRutasArticulos(app);
        configurarRutasComentariosYEtiquetas(app);
        configurarRutasMensajes(app); // Rutas para mensajes

        // Endpoints para la API de chats
        configurarRutasAPIChats(app);
    }

    private static void configurarRutasAPIChats(Javalin app) {
        // Endpoint para obtener la lista de usuarios que han mandado mensajes
        app.get("/api/chats", ctx -> {
            EntityManager em = DatabaseUtil.getEntityManager();
            try {
                List<Object[]> chatsActivos = em.createQuery(
                        "SELECT u.nombre, u.username FROM User u WHERE EXISTS " +
                                "(SELECT m FROM Mensaje m WHERE m.emisor = u)",
                        Object[].class
                ).getResultList();

                List<Map<String, Object>> chats = new ArrayList<>();
                for (Object[] chat : chatsActivos) {
                    Map<String, Object> chatMap = new HashMap<>();
                    chatMap.put("nombre", chat[0]);
                    chatMap.put("alias", chat[1]);
                    chats.add(chatMap);
                }
                ctx.json(chats);
            } finally {
                em.close();
            }
        });

        // Endpoint para obtener el historial de mensajes enviados por un usuario específico
        app.get("/api/chats/{alias}", ctx -> {
            String alias = ctx.pathParam("alias");
            EntityManager em = DatabaseUtil.getEntityManager();
            try {
                List<Mensaje> mensajes = em.createQuery(
                                "SELECT m FROM Mensaje m WHERE m.emisor.username = :alias ORDER BY m.fecha ASC",
                                Mensaje.class
                        )
                        .setParameter("alias", alias)
                        .getResultList();
                ctx.json(mensajes);
            } finally {
                em.close();
            }
        });

        // Nuevo endpoint para que el admin envíe un mensaje a un usuario específico y se guarde en la BD
        app.post("/api/chats/admin", ctx -> {
            User admin = ctx.sessionAttribute("usuario");
            if (admin == null || !admin.isAdmin()) {
                ctx.status(403).result("No autorizado");
                return;
            }
            // Se espera un JSON con "contenido" y "receptor" (el username del usuario a quien se envía el mensaje)
            Map<String, Object> mensajeData = ctx.bodyAsClass(Map.class);
            String contenido = (String) mensajeData.get("contenido");
            String receptorUsername = (String) mensajeData.get("receptor");

            if (contenido == null || contenido.trim().isEmpty() ||
                    receptorUsername == null || receptorUsername.trim().isEmpty()) {
                ctx.status(400).result("Contenido o receptor inválido");
                return;
            }

            EntityManager em = DatabaseUtil.getEntityManager();
            try {
                em.getTransaction().begin();
                Mensaje mensaje = new Mensaje();
                mensaje.setContenido(contenido);
                mensaje.setEmisor(admin);
                // Se obtiene el usuario receptor por su username
                User receptor = em.createQuery("SELECT u FROM User u WHERE u.username = :username", User.class)
                        .setParameter("username", receptorUsername)
                        .getSingleResult();
                mensaje.setReceptor(receptor);
                em.persist(mensaje);
                em.getTransaction().commit();
                ctx.json(mensaje);
            } catch (Exception e) {
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }
                ctx.status(500).result("Error al enviar el mensaje: " + e.getMessage());
            } finally {
                em.close();
            }
        });
    }


    private static void configurarMiddleware(Javalin app) {
        app.before(ctx -> {
            String path = ctx.path();
            if (path.equals("/login") || path.equals("/autenticar") || path.equals("/registro")
                    || path.equals("/") || path.startsWith("/index") || path.startsWith("/articulo/")
                    || path.startsWith("/static") || path.startsWith("/mensajes/obtener") || path.startsWith("/mensajes/enviar")) {
                return;
            }
            User usuario = ctx.sessionAttribute("usuario");
            if (usuario == null) {
                ctx.redirect("/login");
                return;
            }
            if (ctx.path().startsWith("/usuarios") && !usuario.isAdmin()) {
                ctx.redirect("/index");
            }
            if ((ctx.path().startsWith("/mis-articulos") || ctx.path().startsWith("/articulos"))
                    && !usuario.isAdmin() && !usuario.isAutor()) {
                ctx.redirect("/index");
            }
            if (ctx.path().equals("/articulos/nuevo") && (!usuario.isAdmin() && !usuario.isAutor())) {
                ctx.redirect("/login");
            }
        });
    }

    private static void configurarRutasAutenticacion(Javalin app) {
        app.get("/", ctx -> ctx.redirect("/index"));
        app.get("/login", AuthController::mostrarLogin);
        app.post("/autenticar", ctx -> {
            AuthController.login(ctx);
            User usuario = ctx.sessionAttribute("usuario");
            if (usuario != null) {
                registrarLogin(usuario.getUsername());
            }
        });
        app.get("/registro", AuthController::mostrarRegistro);
        app.post("/registro", AuthController::registrar);
        app.post("/usuarios/toggle-admin", AuthController::toggleAdmin);
        app.post("/usuarios/toggle-autor", AuthController::toggleAutor);
        app.post("/usuarios/eliminar", AuthController::eliminarUsuario);
        app.post("/usuarios/actualizar", AuthController::actualizarUsuario);
        app.post("/usuarios/nuevo", AuthController::crearUsuario);
        app.get("/logout", AuthController::logout);
        app.get("/perfil", AuthController::mostrarPerfil);
        app.post("/actualizar-perfil", AuthController::actualizarPerfil);
        app.post("/actualizar-foto", AuthController::actualizarFoto);
    }

    private static void configurarRutasPublicas(Javalin app) {
        app.get("/index", ctx -> {
            User usuario = ctx.sessionAttribute("usuario");
            EntityManager em = DatabaseUtil.getEntityManager();
            try {
                int pagina = ctx.queryParamAsClass("pagina", Integer.class).getOrDefault(1);
                Long etiquetaId = null;
                try {
                    etiquetaId = ctx.queryParamAsClass("etiquetaId", Long.class).getOrDefault(null);
                } catch (Exception e) { /* ignorar error parseo */ }

                int articulosPorPagina = 5;
                int indiceInicial = (pagina - 1) * articulosPorPagina;

                String articulosQueryStr;
                String countQueryStr;

                if (etiquetaId != null) {
                    articulosQueryStr = "SELECT DISTINCT a FROM Articulo a JOIN a.etiquetas e WHERE e.id = :etiquetaId ORDER BY a.fecha DESC";
                    countQueryStr = "SELECT COUNT(DISTINCT a) FROM Articulo a JOIN a.etiquetas e WHERE e.id = :etiquetaId";
                } else {
                    articulosQueryStr = "SELECT a FROM Articulo a ORDER BY a.fecha DESC";
                    countQueryStr = "SELECT COUNT(a) FROM Articulo a";
                }

                TypedQuery<Articulo> articulosQuery = em.createQuery(articulosQueryStr, Articulo.class);
                TypedQuery<Long> countQuery = em.createQuery(countQueryStr, Long.class);

                if (etiquetaId != null) {
                    articulosQuery.setParameter("etiquetaId", etiquetaId);
                    countQuery.setParameter("etiquetaId", etiquetaId);
                }

                List<Articulo> articulos = articulosQuery
                        .setFirstResult(indiceInicial)
                        .setMaxResults(articulosPorPagina)
                        .getResultList();

                Long totalArticulos = countQuery.getSingleResult();
                int totalPaginas = (int) Math.ceil((double) totalArticulos / articulosPorPagina);

                List<Etiqueta> etiquetas = em.createQuery("SELECT DISTINCT e FROM Etiqueta e ORDER BY e.etiqueta", Etiqueta.class)
                        .getResultList();

                if ("XMLHttpRequest".equals(ctx.header("X-Requested-With"))) {
                    Map<String, Object> ajaxModel = new HashMap<>();
                    ajaxModel.put("articulos", convertArticulosToDTO(articulos));
                    ajaxModel.put("paginaActual", pagina);
                    ajaxModel.put("totalPaginas", totalPaginas);
                    ctx.json(ajaxModel);
                    return;
                }

                Map<String, Object> model = new HashMap<>();
                model.put("usuario", usuario);
                model.put("articulos", articulos);
                model.put("paginaActual", pagina);
                model.put("totalPaginas", totalPaginas);
                model.put("etiquetas", etiquetas != null ? etiquetas : List.of());

                ctx.render("index.html", model);
            } finally {
                em.close();
            }
        });

        app.get("/articulo/{id}", ctx -> {
            User usuario = ctx.sessionAttribute("usuario");
            long articuloId;
            try {
                articuloId = Long.parseLong(ctx.pathParam("id"));
            } catch (NumberFormatException e) {
                ctx.status(400).result("ID inválido");
                return;
            }
            EntityManager em = DatabaseUtil.getEntityManager();
            try {
                Articulo articulo = em.find(Articulo.class, articuloId);
                if (articulo == null) {
                    ctx.status(404).result("Artículo no encontrado");
                    return;
                }
                Map<String, Object> model = new HashMap<>();
                model.put("articulo", articulo);
                model.put("usuario", usuario);
                ctx.render("articulo.html", model);
            } finally {
                em.close();
            }
        });

    }

    private static void configurarRutasPrivadas(Javalin app) {
        // Muestra la misma lista de usuarios que HAN ENVIADO mensajes en /admin/chats
        app.get("/admin/chats", ctx -> {
            User usuario = ctx.sessionAttribute("usuario");
            if (usuario == null || !usuario.isAdmin()) {
                ctx.redirect("/index");
                return;
            }
            EntityManager em = DatabaseUtil.getEntityManager();
            try {
                // De nuevo, cambiamos la condición a "m.emisor = u"
                List<Object[]> chatsActivos = em.createQuery(
                        "SELECT u.nombre, u.username FROM User u WHERE EXISTS " +
                                "(SELECT m FROM Mensaje m WHERE m.emisor = u)",
                        Object[].class
                ).getResultList();

                Map<String, Object> model = new HashMap<>();
                model.put("usuario", usuario);
                model.put("chats", chatsActivos);
                ctx.render("admin-chats.html", model);
            } finally {
                em.close();
            }
        });

        app.get("/usuarios", ctx -> {
            EntityManager em = DatabaseUtil.getEntityManager();
            try {
                List<User> usuarios = em.createQuery("SELECT u FROM User u", User.class)
                        .getResultList();
                ctx.render("usuarios.html", Map.of("usuarios", usuarios));
            } finally {
                em.close();
            }
        });
    }

    private static void configurarRutasArticulos(Javalin app) {
        app.get("/mis-articulos", ctx -> {
            User usuario = ctx.sessionAttribute("usuario");
            EntityManager em = DatabaseUtil.getEntityManager();
            try {
                List<Articulo> articulos;
                if (usuario.isAdmin()) {
                    articulos = em.createQuery("SELECT a FROM Articulo a ORDER BY a.fecha DESC", Articulo.class)
                            .getResultList();
                } else {
                    articulos = em.createQuery("SELECT a FROM Articulo a WHERE a.autor.id = :id ORDER BY a.fecha DESC", Articulo.class)
                            .setParameter("id", usuario.getId())
                            .getResultList();
                }
                ctx.render("mis-articulos.html", Map.of("articulos", articulos, "usuario", usuario));
            } finally {
                em.close();
            }
        });

        app.get("/articulos/nuevo", BlogController::mostrarFormularioCrear);
        app.post("/articulos/nuevo", BlogController::crearArticulo);
        app.post("/articulo/{id}/eliminar", BlogController::eliminarArticulo);
        app.get("/articulo/{id}/editar", BlogController::mostrarFormularioEditar);
        app.post("/articulo/{id}/editar", BlogController::actualizarArticulo);
    }

    private static void configurarRutasComentariosYEtiquetas(Javalin app) {
        app.get("/articulo/{articuloId}/comentarios", CommentController::listarComentarios);
        app.post("/comentario/nuevo", CommentController::crearComentario);
        app.post("/comentario/{id}/eliminar", CommentController::eliminarComentario);

        app.get("/articulo/{articuloId}/etiquetas", TagController::listarEtiquetas);
        app.post("/etiqueta/nueva", TagController::agregarEtiqueta);
        app.post("/etiqueta/{id}/eliminar", TagController::eliminarEtiqueta);
    }

    // Configuración del WebSocket para el chat (usuarios y admin)
    private static void configurarWebSocket(Javalin app) {
        app.ws("/ws/chat", ws -> {
            ws.onConnect(ctx -> {
                wsSessions.add(ctx);
                // Si el usuario es admin, enviar el historial completo de mensajes
                User user = ctx.sessionAttribute("usuario");
                if (user != null && user.isAdmin()) {
                    EntityManager em = DatabaseUtil.getEntityManager();
                    try {
                        List<Mensaje> mensajes = em.createQuery("SELECT m FROM Mensaje m ORDER BY m.fecha ASC", Mensaje.class)
                                .getResultList();
                        String json = new ObjectMapper().writeValueAsString(mensajes);
                        ctx.send(json);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        em.close();
                    }
                }
            });

            ws.onClose(ctx -> wsSessions.remove(ctx));

            ws.onMessage(ctx -> {
                User emisor = ctx.sessionAttribute("usuario");
                if (emisor == null) return;
                String msgJson = ctx.message();
                ObjectMapper mapper = new ObjectMapper();
                try {
                    // Se espera un JSON con "contenido" y "receptor" (username)
                    Map<String, String> msgData = mapper.readValue(msgJson, Map.class);
                    String contenido = msgData.get("contenido");
                    String receptorUsername = msgData.get("receptor");

                    EntityManager em = DatabaseUtil.getEntityManager();
                    try {
                        User receptor = em.createQuery("SELECT u FROM User u WHERE u.username = :username", User.class)
                                .setParameter("username", receptorUsername)
                                .getSingleResult();

                        em.getTransaction().begin();
                        Mensaje mensaje = new Mensaje();
                        mensaje.setContenido(contenido);
                        mensaje.setEmisor(emisor);
                        mensaje.setReceptor(receptor);
                        em.persist(mensaje);
                        em.getTransaction().commit();

                        // Convertir el mensaje a JSON y hacer broadcast
                        String mensajeJson = mapper.writeValueAsString(mensaje);
                        wsSessions.forEach(session -> {
                            try {
                                session.send(mensajeJson);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                    } finally {
                        em.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        });
    }

    private static List<Map<String, Object>> convertArticulosToDTO(List<Articulo> articulos) {
        return articulos.stream().map(a -> {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id", a.getId());
            dto.put("titulo", a.getTitulo());
            String cuerpo = a.getCuerpo();
            if (cuerpo.length() > 70) {
                String truncated = cuerpo.substring(0, 70) + "... <a href='/articulo/" + a.getId() + "'>Leer más</a>";
                dto.put("cuerpo", truncated);
            } else {
                dto.put("cuerpo", cuerpo);
            }
            dto.put("fecha", a.getFecha());
            if (a.getAutor() != null) {
                dto.put("autor", a.getAutor().getNombre());
            }
            return dto;
        }).collect(Collectors.toList());
    }
}
