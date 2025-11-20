package Controladores;

import app.java.DatabaseUtil;
import io.javalin.http.Context;
import modelos.User;
import org.jasypt.util.text.BasicTextEncryptor;
import servicios.UsuarioServicios;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class AuthController {

    private static final String COOKIE_NAME = "rememberMe";
    private static final String ENCRYPTION_PASSWORD = "claveSecreta";

    public static void mostrarLogin(Context ctx) {
        // Verificar si hay una cookie de "Recordar usuario"
        String encryptedUsername = ctx.cookie(COOKIE_NAME);
        if (encryptedUsername != null) {
            BasicTextEncryptor encryptor = new BasicTextEncryptor();
            encryptor.setPassword(ENCRYPTION_PASSWORD);
            String username = encryptor.decrypt(encryptedUsername);

            // Autenticar automáticamente al usuario
            EntityManager em = DatabaseUtil.getEntityManager();
            try {
                User usuario = em.createQuery(
                                "SELECT u FROM User u WHERE u.username = :username", User.class)
                        .setParameter("username", username)
                        .getSingleResult();

                ctx.sessionAttribute("usuario", usuario);
                ctx.redirect("/index");
                return;
            } catch (NoResultException e) {
                // Si no se encuentra el usuario, simplemente mostrar el formulario de login
            } finally {
                em.close();
            }
        }

        // Mostrar el formulario de login
        ctx.render("login.html");
    }

    public static void login(Context ctx) {
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");
        boolean recordar = ctx.formParam("recordar") != null; // Verificar si el checkbox está marcado

        // Use UsuarioServicios which checks bcrypt-hashed passwords
        User usuario = UsuarioServicios.autenticar(username, password);
        if (usuario != null) {
            ctx.sessionAttribute("usuario", usuario);

            // Si el usuario marcó "Recordar usuario", crear una cookie encriptada
            if (recordar) {
                BasicTextEncryptor encryptor = new BasicTextEncryptor();
                encryptor.setPassword(ENCRYPTION_PASSWORD);
                String encryptedUsername = encryptor.encrypt(username);

                // Crear la cookie con una duración de 1 semana (604800 segundos)
                ctx.cookie(COOKIE_NAME, encryptedUsername, 604800);
            }

            ctx.redirect("/index");
        } else {
            ctx.attribute("error", "Usuario o contraseña incorrectos");
            ctx.render("login.html");
        }
    }

    public static void logout(Context ctx) {
        // Eliminar la cookie "Recordar usuario"
        ctx.removeCookie(COOKIE_NAME);

        // Invalidar la sesión
        ctx.sessionAttribute("usuario", null);
        ctx.redirect("/login");
    }

    public static void toggleAdmin(Context ctx) {
        // Se recibe el id en lugar del username
        Long id = Long.parseLong(ctx.formParam("id"));
        User usuarioSesion = ctx.sessionAttribute("usuario");

        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            User user = em.find(User.class, id);
            if (user != null) {
                // Comparar utilizando el id
                if (user.getId().equals(usuarioSesion.getId())) {
                    ctx.sessionAttribute("usuario", null);
                    em.getTransaction().commit();
                    ctx.redirect("/login");
                    return;
                }
                user.setAdmin(!user.isAdmin());
                em.merge(user);
            }

            em.getTransaction().commit();
            ctx.redirect("/usuarios");
        } finally {
            em.close();
        }
    }

    public static void toggleAutor(Context ctx) {
        Long id = Long.parseLong(ctx.formParam("id"));

        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            User user = em.find(User.class, id);
            if (user != null) {
                user.setAutor(!user.isAutor());
                em.merge(user);
            }

            em.getTransaction().commit();
            ctx.redirect("/usuarios");
        } finally {
            em.close();
        }
    }

    public static void eliminarUsuario(Context ctx) {
        Long id = Long.parseLong(ctx.formParam("id"));

        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            User user = em.find(User.class, id);
            if (user != null) {
                em.remove(user);
            }

            em.getTransaction().commit();
            ctx.redirect("/usuarios");
        } finally {
            em.close();
        }
    }

    public static void actualizarUsuario(Context ctx) {
        String nombre = ctx.formParam("nombre");
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");
        // Ahora se recibe el id en lugar del originalUsername
        Long id = Long.parseLong(ctx.formParam("id"));

        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            // Verificar username único (excluyendo el usuario con este id)
            boolean existe = em.createQuery(
                            "SELECT COUNT(u) FROM User u WHERE u.username = :username AND u.id != :id", Long.class)
                    .setParameter("username", username)
                    .setParameter("id", id)
                    .getSingleResult() > 0;

            if (existe) {
                ctx.attribute("error", "El nombre de usuario ya está en uso");
                List<User> usuarios = em.createQuery("SELECT u FROM User u", User.class).getResultList();
                ctx.render("usuarios.html", Map.of("usuarios", usuarios));
                return;
            }

            User usuario = em.find(User.class, id);
            if (usuario != null) {
                usuario.setNombre(nombre);
                usuario.setUsername(username);
                usuario.setPassword(password);
                em.merge(usuario);

                // Actualizar sesión si es el mismo usuario
                User sesion = ctx.sessionAttribute("usuario");
                if (sesion != null && sesion.getId().equals(id)) {
                    ctx.sessionAttribute("usuario", usuario);
                }
            }

            em.getTransaction().commit();
            ctx.redirect("/usuarios");
        } finally {
            em.close();
        }
    }

    public static void crearUsuario(Context ctx) {
        String nombre = ctx.formParam("nombre");
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");
        boolean isAdmin = ctx.formParam("admin") != null;
        boolean isAutor = ctx.formParam("autor") != null;

        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            // Verificar si existe
            Long count = em.createQuery(
                            "SELECT COUNT(u) FROM User u WHERE u.username = :username", Long.class)
                    .setParameter("username", username)
                    .getSingleResult();

            if (count > 0) {
                ctx.attribute("error", "El username ya está en uso");
                List<User> usuarios = em.createQuery("SELECT u FROM User u", User.class).getResultList();
                ctx.render("usuarios.html", Map.of("usuarios", usuarios));
                return;
            }

            User nuevoUsuario = new User();
            nuevoUsuario.setNombre(nombre);
            nuevoUsuario.setUsername(username);
            nuevoUsuario.setPassword(password);
            nuevoUsuario.setAdmin(isAdmin);
            nuevoUsuario.setAutor(isAutor);

            em.persist(nuevoUsuario);
            em.getTransaction().commit();

            ctx.redirect("/usuarios");
        } finally {
            em.close();
        }
    }

    public static void mostrarRegistro(Context ctx) {
        ctx.render("registro.html");
    }

    public static void registrar(Context ctx) {
        String nombre = ctx.formParam("nombre");
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");

        EntityManager em = DatabaseUtil.getEntityManager();
        try {
            em.getTransaction().begin();

            // Verificar si el username ya existe
            boolean existe = em.createQuery(
                            "SELECT COUNT(u) FROM User u WHERE u.username = :username", Long.class)
                    .setParameter("username", username)
                    .getSingleResult() > 0;

            if (existe) {
                ctx.attribute("error", "El nombre de usuario ya está en uso");
                ctx.render("registro.html");
                return;
            }

            // Crear nuevo usuario
            User nuevoUsuario = new User();
            nuevoUsuario.setNombre(nombre);
            nuevoUsuario.setUsername(username);
            nuevoUsuario.setPassword(password);

            // Manejo de la foto de perfil
            boolean fotoSubida = false;

            // Procesar foto subida
            for (var uploadedFile : ctx.uploadedFiles("foto")) {
                if (uploadedFile != null && !uploadedFile.filename().isEmpty()) {
                    try {
                        byte[] bytes = uploadedFile.content().readAllBytes();
                        String encodedString = Base64.getEncoder().encodeToString(bytes);

                        nuevoUsuario.setFotoNombre(uploadedFile.filename());
                        nuevoUsuario.setFotoMimeType(uploadedFile.contentType());
                        nuevoUsuario.setFotoBase64(encodedString);
                        fotoSubida = true;

                    } catch (IOException e) {
                        ctx.status(500).result("Error al procesar la foto subida");
                        return;
                    }
                }
            }

            // Asignar foto por defecto si no se subió ninguna
            if (!fotoSubida) {
                try (InputStream defaultFotoStream = Thread.currentThread()
                        .getContextClassLoader()
                        .getResourceAsStream("publico/default.png")) {

                    if (defaultFotoStream == null) {
                        throw new IOException("No se encontró la imagen por defecto");
                    }

                    byte[] bytes = defaultFotoStream.readAllBytes();
                    String encodedString = Base64.getEncoder().encodeToString(bytes);

                    nuevoUsuario.setFotoNombre("default.png");
                    nuevoUsuario.setFotoMimeType("image/png");
                    nuevoUsuario.setFotoBase64(encodedString);

                } catch (IOException e) {
                    ctx.status(500).result("Error al cargar la foto por defecto: " + e.getMessage());
                    return;
                }
            }

            em.persist(nuevoUsuario);
            em.getTransaction().commit();

            ctx.sessionAttribute("usuario", nuevoUsuario);
            ctx.redirect("/index");

        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            ctx.status(500).result("Error al registrar usuario: " + e.getMessage());
        } finally {
            em.close();
        }
    }
    public static void mostrarPerfil(Context ctx) {
        User usuario = ctx.sessionAttribute("usuario");
        if (usuario == null) {
            ctx.redirect("/login");
            return;
        }
        ctx.render("perfil.html", Map.of("usuario", usuario));
    }

    public static void actualizarPerfil(Context ctx) {
        User usuario = ctx.sessionAttribute("usuario");
        if (usuario == null) {
            ctx.redirect("/login");
            return;
        }

        // Actualizar datos básicos
        usuario.setNombre(ctx.formParam("nombre"));
        usuario.setUsername(ctx.formParam("username"));

        UsuarioServicios.actualizarUsuario(usuario);
        ctx.sessionAttribute("usuario", usuario); // Actualizar sesión
        ctx.redirect("/index");
    }

    public static void actualizarFoto(Context ctx) {
        User usuario = ctx.sessionAttribute("usuario");
        ctx.uploadedFiles("foto").forEach(uploadedFile -> {
            try {
                byte[] bytes = uploadedFile.content().readAllBytes();
                String encodedString = Base64.getEncoder().encodeToString(bytes);

                usuario.setFotoNombre(uploadedFile.filename());
                usuario.setFotoMimeType(uploadedFile.contentType());
                usuario.setFotoBase64(encodedString);

                UsuarioServicios.actualizarUsuario(usuario);

            } catch (IOException e) {
                ctx.status(500).result("Error al procesar la foto");
            }
        });
        ctx.redirect("/perfil");
    }
}
