README - Blog_CRUD
====================

Resumen general
----------------
Blog_CRUD es una aplicación web de ejemplo escrita en Java y construida con Gradle. Es una pequeña plataforma de blogs con:
- Gestión de usuarios (registro, login, perfil, roles admin/autor).
- CRUD de artículos (crear, leer, actualizar, eliminar).
- Sistema de etiquetas (etiquetas vinculadas a artículos, relación many-to-many).
- Comentarios en artículos.
- Sistema de mensajería/chat (mensajes públicos/privados con persistencia).
- Páginas del lado del servidor renderizadas con Thymeleaf.
- Persistencia JPA/Hibernate sobre H2 (servidor TCP) por defecto. Contiene utilidades para iniciar la consola web de H2.

Objetivo del README
-------------------
Este README detalla absolutamente todo lo importante dentro del proyecto: estructura de carpetas y archivos, tecnologías y dependencias, arquitectura (MVC), modelos y relaciones JPA, controladores y servicios, configuración de persistencia, cómo compilar/ejecutar y notas de seguridad y mejoras.

Estructura del proyecto (ubicaciones clave)
-------------------------------------------
Raíz del proyecto contiene (no listadas subcarpetas vacías):
- `build.gradle` - archivo de configuración de Gradle (dependencias y aplicación principal).
- `gradlew`, `gradlew.bat`, `gradle/` - wrapper de Gradle.
- `src/main/java/` - código fuente Java (controladores, servicios, modelos, utilidades).
- `src/main/resources/` - recursos de la aplicación (plantillas Thymeleaf, `META-INF/persistence.xml`, recursos estáticos como CSS).

Detalle por carpetas y archivos más relevantes
---------------------------------------------
1) `src/main/java/app/java/`
   - `App.java` (clase principal - `public static void main`):
     - Configura y arranca Javalin.
     - Configura Thymeleaf (TemplateEngine) y el motor de plantillas.
     - Configura serializadores Jackson (incluye módulo Hibernate5 para tratar LAZY y evitar referencias infinitas al serializar).
     - Contiene rutas (endpoints) para páginas y APIs, configuración de middlewares, websockets (se mantiene un conjunto `wsSessions` para sesiones activas), y lógica de inicialización de base de datos/registro en caso de que no se use `JDBC_DATABASE_URL`.
   - `DatabaseUtil.java`:
     - Encapsula la creación y la gestión del `EntityManagerFactory` y arranca un servidor H2 en modo TCP (puerto 9092). También ofrece método para iniciar la consola Web de H2 (puerto 8082) para desarrollo.
     - Registra un shutdown hook para cerrar el `EntityManagerFactory` y detener el servidor H2 cuando la JVM finaliza.
   - `DataSourceConfig.java`:
     - Provee un `PGSimpleDataSource` configurado a partir de la variable de entorno `JDBC_DATABASE_URL`. Si la variable no existe lanza RuntimeException. Es útil si se desea usar PostgreSQL en producción.

2) `src/main/java/Controladores/` (controllers - manejan requests/response y renderizan vistas o devuelven JSON)
   - `AuthController.java`:
     - Login, logout, registro de usuarios, manejo de cookie "rememberMe" (con cifrado simple por `jasypt`), toggles de roles (`admin`, `autor`), crear/actualizar/eliminar usuarios, mostrar perfil.
     - Usa `UsuarioServicios` para operaciones persistentes.
   - `BlogController.java`:
     - Formulario para crear artículos, crear/editar/eliminar artículos, procesamiento de etiquetas, ver lista de artículos del usuario / admin.
   - `CommentController.java`:
     - Crear y eliminar comentarios (verifica autorización), listar comentarios por artículo.
   - `MensajeController.java`:
     - Obtener y enviar mensajes (maneja casos de usuarios no autenticados con campo `emisorAnonimo`), reglas de borrado de mensajes (solo emisor o admin), endpoints para administración de chats.
   - `TagController.java`:
     - Gestión de etiquetas: listar etiquetas de un artículo, agregar etiqueta, eliminar etiqueta (crea etiqueta si no existe).

3) `src/main/java/servicios/` (lógica de negocio / acceso a datos simplificado)
   - `ArticuloServicios.java`:
     - Métodos para agregar, listar, obtener por id, actualizar y eliminar artículos.
   - `ChatServicios.java`:
     - Métodos para enviar y recuperar mensajes y eliminar mensajes con reglas de permiso.
   - `ComentarioServicios.java`:
     - Crear y eliminar comentarios; actualiza relaciones bidireccionales (artículo - comentario).
   - `UsuarioServicios.java`:
     - Autenticar (consulta por username+password), listar usuarios, actualizar usuario, obtener usuario por username.

4) `src/main/java/modelos/` (entidades JPA / domain model)
   - `User.java` (tabla `usuarios`):
     - Campos principales: `id` (Long PK), `username` (unique), `nombre`, `password`, `admin` (boolean), `autor` (boolean).
     - Relaciones: `@OneToMany` con `Articulo` (autor) y `Comentario` (autor).
     - Campos para foto: `fotoNombre`, `fotoMimeType`, `fotoBase64` (se guarda la imagen en Base64 en la BD como `@Lob`).
   - `Articulo.java`:
     - Campos: `id`, `titulo`, `cuerpo` (Lob), `autor` (ManyToOne -> User), `fecha` (timestamp).
     - Relaciones: `@OneToMany` comentarios (cascade ALL, orphanRemoval) y `@ManyToMany` etiquetas (tabla join `articulo_etiqueta`).
   - `Comentario.java`:
     - Campos: `id`, `comentario`(Lob), `autor` (ManyToOne -> User), `articulo` (ManyToOne -> Articulo).
   - `Etiqueta.java`:
     - Campos: `id`, `etiqueta` (unique), `@ManyToMany(mappedBy = "etiquetas")` con `Articulo`.
   - `Mensaje.java`:
     - Campos: `id`, `contenido` (Lob), `emisor` (ManyToOne -> User, nullable), `emisorAnonimo` (String), `receptor` (ManyToOne -> User), `fecha` (timestamp).

5) `src/main/resources/META-INF/persistence.xml`
   - Define `persistence-unit` llamado `blogPU` (transaction-type RESOURCE_LOCAL).
   - Provider: `org.hibernate.jpa.HibernatePersistenceProvider`.
   - Enumera explícitamente las entidades: `modelos.Mensaje`, `modelos.User`, `modelos.Articulo`, `modelos.Comentario`, `modelos.Etiqueta`.
   - Configuración JDBC para H2 en modo servidor: `javax.persistence.jdbc.url` apunta a `jdbc:h2:tcp://localhost//tmp/blogdb;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE`.
   - Hibernate: `hbm2ddl.auto = update`, `show_sql = true`, `format_sql = true`.

6) Plantillas Thymeleaf (`src/main/resources/templates/`)
   - `index.html` - página principal (lista de artículos, chat flotante, paginación, filtros por etiquetas).
   - `articulo.html` - vista detalle de un artículo y comentarios.
   - `mis-articulos.html` - interfaz para crear/editar/gestionar artículos y etiquetas (incluye modales JS de Bootstrap).
   - `login.html`, `registro.html`, `perfil.html` - formularios de autenticación y perfil.
   - `Usuarios.html` - listado y edición de usuarios (solo visible para admin según la plantilla).
   - `admin-chats.html` - interfaz administrativa para revisar historiales de chat, abrir chats de usuarios.
   - `layout.html` - plantilla base (fragmento Thymeleaf para layout).

7) Recursos estáticos
   - `src/main/resources/Static/styles.css` - CSS (actualmente vacío en el repo suministrado). 
   - Las plantillas cargan CSS/JS externos desde CDN (Bootstrap, Bootstrap Icons) para UI.

8) Otros archivos enlazados en la raíz
   - `txt.txt` - contiene justificación del proyecto (documento de texto agregado por el autor).

Configuración de build y dependencias
------------------------------------
- `build.gradle` define el plugin `java` y `application`. `mainClass` apuntando a `app.java.App`.
- Repositorio: mavenCentral().
- Dependencias destacadas (versión a fecha del proyecto):
  - `io.javalin:javalin:6.4.0` (framework web minimalista sobre Jetty/Netty).
  - `io.javalin:javalin-rendering` (renderizado adicional para plantillas)
  - `org.thymeleaf:thymeleaf` (motor de plantillas Thymeleaf para render del lado del servidor).
  - `com.h2database:h2` (base de datos embebida/servidor H2).
  - `org.hibernate:hibernate-core` y `hibernate-entitymanager` (implementación JPA/Hibernate).
  - `javax.persistence:javax.persistence-api` (JPA API)
  - `com.fasterxml.jackson.core:jackson-databind` y `jackson-datatype-hibernate5` (serialización JSON y soporte para proxies Hibernate)
  - `org.jasypt:jasypt` (cifrado simple para cookies)
  - `org.postgresql:postgresql` (driver para PostgreSQL; usado si se define `JDBC_DATABASE_URL`).
  - `org.slf4j:slf4j-simple` (logger simple para salida en consola).

Cómo compilar y ejecutar (Windows PowerShell)
---------------------------------------------
Requisitos previos:
- Java JDK 17+ (compatible con el bytecode y dependencias usadas).
- Gradle wrapper incluido, por lo que no es necesario tener Gradle instalado globalmente.

Comandos recomendados (desde la raíz del proyecto, en PowerShell):

# Compilar y ejecutar la aplicación
./gradlew.bat build; ./gradlew.bat run

Notas: `./gradlew.bat run` ejecutará la clase `app.java.App` (configurada en `build.gradle`).

Si quieres ejecutar sin build explícito (run hace build incremental):
./gradlew.bat run

Acceder a la app (por defecto Javalin suele correr en http://localhost:7000 salvo si `App.java` cambia el puerto). Verifica la salida en la consola para el puerto exacto.

Usar H2 (consola Web) - desarrollo:
- `DatabaseUtil.iniciarConsolaWeb()` puede ser llamado o invocado en código; sin embargo `DatabaseUtil` ya arranca un servidor TCP en 9092 por defecto.
- Luego abrir en navegador: http://localhost:8082 (si la consola web está iniciada) y conectar con URL JDBC tal como aparece en `persistence.xml` (ej. jdbc:h2:tcp://localhost//tmp/blogdb).

Uso con PostgreSQL (producción/opcional):
- En `DataSourceConfig` se intenta leer `JDBC_DATABASE_URL` de variables de entorno y configurar `PGSimpleDataSource`.
- Si prefieres PostgreSQL, define la variable de entorno `JDBC_DATABASE_URL` con la cadena JDBC adecuada y ajusta `persistence.xml` o la configuración de Hibernate para usar Postgres.

Endpoints y rutas (visuales aproximadas)
---------------------------------------
Las rutas concretas se definen en `App.java` (no se han mostrado todas las líneas en el adjunto), pero por convención y por el contenido de controladores existe:
- GET `/index` o `/` - lista principal de artículos.
- GET `/articulo/{id}` - ver detalle del artículo.
- POST `/comentarios` o `/comentario` - crear comentario.
- GET/POST `/login`, `/autenticar` - login.
- GET/POST `/registro` - registro de usuarios.
- GET `/mis-articulos` - panel de usuario para crear/editar sus artículos (solo autores).
- POST `/articulo/crear`, `/articulo/{id}/editar`, `/articulo/{id}/eliminar` - operaciones CRUD.
- Endpoints para etiquetas: `/articulo/{articuloId}/etiquetas`, `/etiqueta/{id}/eliminar`.
- Chat / Mensajes: endpoints JSON para enviar/obtener mensajes (usados por chat flotante en `index.html` y por `admin-chats.html`), además de WebSocket handling si está implementado en `App.java`.
- Endpoints administrativos: `/usuarios` y operaciones para editar/eliminar usuarios (solo admin).

Detalle de relaciones y reglas de negocio principales
----------------------------------------------------
- `User` 1..* Articulos (un usuario puede ser autor de muchos artículos).
- `Articulo` *..* `Etiqueta` (tabla join `articulo_etiqueta`).
- `Articulo` 1..* `Comentario` (cascade ALL, orphanRemoval=true) - eliminar artículo borra comentarios.
- Mensajes pueden ser enviados por usuarios autenticados (`emisor`), o por anónimos usando `emisorAnonimo`. Receptor opcional para conversaciones.
- Roles: `admin` y `autor` booleanos en `User` controlan visibilidad y permisos (p. ej. admin puede ver y borrar mensajes de otros).

Persistencia y consideraciones técnicas
--------------------------------------
- JPA con Hibernate maneja el mapeo objeto-relacional. `persistence.xml` usa `hibernate.hbm2ddl.auto = update` para sincronizar esquema en desarrollo.
- `DatabaseUtil` arranca un servidor H2 en modo TCP para permitir conexiones externas (útil para abrir la consola de H2 o acceder desde una herramienta externa).
- Jackson se configura con `Hibernate5Module` para gestionar serialización de entidades con proxies y evitar problemas con LAZY load. Sin embargo, serializar entidades JPA directamente a JSON en controladores requiere cuidado para no abrir sesiones fuera del `EntityManager`.

Puntos de seguridad (observaciones importantes)
-----------------------------------------------
- Almacenamiento de contraseñas: `UsuarioServicios.autenticar` hace una consulta por `username` y `password` (lo que sugiere que las contraseñas se almacenan en texto plano). Recomendación urgente: usar algoritmos de hashing seguros (BCrypt, Argon2) y no almacenar contraseñas en claro.
- CSRF: Formularios POST no muestran explícitamente tokens CSRF. Si la app se expone, habilitar protección CSRF es recomendado.
- Validación y saneamiento: Entrada de usuarios (comentarios, títulos, cuerpo, valores en templates) debe ser saneada correctamente para evitar XSS. Thymeleaf escapa por defecto, pero revisar lugares donde `th:utext` o impresión sin escape sean usados.
- Cookies: hay cookie `rememberMe` cifrada con `jasypt` (mejor que nada), pero revisar expiración y uso de `HttpOnly` y `Secure` flags.
- Websockets/Chat: validar mensajes, evitar inyección de scripts en chats, sanear contenido antes de renderizar.

Notas sobre diseño y calidad
---------------------------
- Separación de responsabilidades: existe capa de `Controladores` (HTTP), `Servicios` (lógica de negocio) y `Modelos` (entidades). Buena base para mantenimiento.
- Uso de `EntityManager` manual en servicios/controladores: es claro pero requiere cuidado con gestión de transacciones y cierre del `EntityManager`.
- Tests: no hay tests unitarios/integración incluidos; añadir tests (JUnit + H2 en memoria) es una mejora recomendada.

Recomendaciones y mejoras sugeridas (próximos pasos)
--------------------------------------------------
1. Password hashing: implementar BCrypt (por ejemplo `spring-security-core` o `BCrypt` de jBCrypt) y actualizar el flujo de registro / autenticación.
2. Añadir validaciones (Bean Validation - javax.validation) en entidades y validar entradas en controladores.
3. Añadir tests automatizados (mocks para servicios y tests integrados con H2 en memoria).
4. Configurar perfiles (dev/prod) para no usar `hbm2ddl.auto=update` en producción.
5. Externalizar configuración (por ejemplo archivo `application.properties` o variables de entorno) y mejorar `DataSourceConfig` para seleccionar entre H2 y PostgreSQL en función de ENV.
6. Mejorar almacenamiento de archivos (en lugar de Base64 en DB, usar almacenamiento de archivos y solo guardar rutas en DB) si el tamaño de imágenes crece.
7. Añadir control de errores centralizado y páginas de error amigables.

Cómo explorar el código localmente (sugerencia rápida)
-----------------------------------------------------
1. Abrir el proyecto en un IDE (IntelliJ IDEA, Eclipse). Importar como proyecto Gradle.
2. Ejecutar `./gradlew.bat run` desde PowerShell para levantar el servidor.
3. Abrir `src/main/resources/templates/` para revisar vistas y cómo se consumen los endpoints del backend.
4. Abrir `src/main/java/Controladores/` para ver el manejo de rutas y `src/main/java/servicios/` para la lógica de negocio.
5. Conectar a la consola H2 para inspeccionar la BD (si `DatabaseUtil` la inicia): http://localhost:8082 (con URL JDBC: `jdbc:h2:tcp://localhost//tmp/blogdb`) y credenciales `sa`/"" (según `persistence.xml`).

Notas finales y responsabilidades del autor
-------------------------------------------
- El proyecto está pensado como ejemplo didáctico y base para una aplicación CRUD de blogs con chat. Algunas decisiones (p. ej., contraseñas en claro, almacenamiento de imágenes como Base64) son prácticas para prototipos pero deben revisarse antes de desplegar a producción.

Archivos creados/seguimiento
----------------------------
- README.txt (este archivo) creado en la raíz del proyecto para ofrecer la vista general solicitada.

Soporte y contacto
------------------
Si necesitas que genere un README en otro formato (README.md con Markdown, integración de diagrama de entidades, o que haga cambios automáticos en el código para mejorar seguridad o añadir tests), dímelo y lo implemento.

---
Fin del README detallado para el proyecto Blog_CRUD.
