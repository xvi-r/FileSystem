# AirTortilla — Sistema de Gestión y Partición de Reservas de Vuelo

Proyecto desarrollado en **Java con Spring Boot** e interfaz web para el procesamiento, partición y consulta de reservas de vuelos para la aerolínea **AirTortilla**, correspondiente a la asignatura de **Acceso a Datos (AAD)**.

> **Entrega 2**: Para esta segunda parte de la práctica hemos migrado toda la persistencia que teníamos antes con ficheros CSV en disco a una **Base de Datos Relacional (PostgreSQL)** usando **Spring Data JPA e Hibernate**. También hemos metido **seguridad con login y roles (US1 y US2)**, **trazabilidad de cambios con logs de auditoría (US3)** y empaquetado todo con **Docker Compose** para que se levante fácil con un solo comando.

---

## Diagrama del Sistema

[![Diagrama Base del Sistema](images/diagrama_base.svg)](images/diagrama_base.svg)

---

## Qué cambia en esta Entrega 2 (Migración a PostgreSQL)

En la primera entrega del proyecto estábamos guardando y particionando los ficheros CSV directamente en el disco duro del servidor dentro de la carpeta `uploads/reservas/{DESTINO}/`. 

Como en el enunciado nos pedían migrar la infraestructura por normativa legal a una base de datos relacional, hemos cambiado la arquitectura:

1. **Persistencia en Base de Datos**: Ahora todos los billetes y los pasajeros se guardan en tablas de PostgreSQL en vez de crear carpetas y ficheros en disco.
2. **Procesamiento directo en memoria (sin tocar disco)**: Cuando el usuario sube un CSV desde la web o el endpoint, lo leemos línea a línea con un `InputStreamReader` y `BufferedReader` y lo metemos directo a la base de datos. Ya no creamos archivos temporales en el disco ni dejamos basura guardada
3. **Descarga del ZIP al vuelo**: El endpoint del ZIP (`/files/zip`) ahora consulta las reservas que hay en PostgreSQL agrupadas por destino, monta los CSVs en memoria y los comprime directamente con `ZipOutputStream` en un flujo de bytes que se manda al navegador para descargar

---

## User Stories de la Entrega 2

### US1: Autenticación de Empleados
> *Como Empleado Quiero Poder hacer log in para acceder a los recursos de la compañía*

- Hemos configurado **Spring Security** para que maneje la autenticación mediante sesiones HTTP (`JSESSIONID`).
- Las contraseñas de los usuarios no se guardan en texto plano en la base de datos, las encriptamos con `BCryptPasswordEncoder` que le mete un salt aleatorio
- Las rutas para consultar vuelos, subir CSVs o bajarse el zip están protegidas y si no has iniciado sesión te devuelve un error `401 Unauthorized`.
- Al arrancar la aplicación por primera vez, un seeder (`DataInitializer`) crea automáticamente la cuenta de admin (`admin` / `admin123`) si la base de datos está vacía.

### US2: Altas y Gestión de Usuarios por el Administrador
> *Como Administrador de Sistemas quiero poder dar de alta usuarios para garantizar la seguridad interna de la aplicación*

- Tenemos dos roles definidos en el enum `Role`: `ROLE_EMPLOYEE` (empleado normal) y `ROLE_ADMIN` (sysadmin).
- Solo los administradores pueden entrar al endpoint `POST /api/admin/users` gracias a la anotación `@PreAuthorize("hasRole('ADMIN')")`.
- El admin puede dar de alta nuevos empleados indicando usuario, clave, email y rol
- También se puede bloquear o desbloquear a un usuario con `PATCH /api/admin/users/{id}/status`. Si un empleado está deshabilitado (`enabled = false`), Spring Security no le deja entrar aunque ponga bien la contraseña
- Hay un endpoint `GET /api/admin/users` para que el admin pueda ver la lista de todos los usuarios en el panel.

### US3: Trazabilidad y Auditoría de Modificaciones
> *Como Administrador de Sistemas Quiero mantener la trazabilidad de las modificaciones para mantener las buenas prácticas de seguridad en la aplicación*

- Hemos creado la tabla `audit_logs` donde se guarda automáticamente un rastro de todo lo que pasa en el sistema:
  - Cuando se inserta una reserva (`RESERVA_CREATED`).
  - Cuando se detecta y se resuelve una colisión (`COLLISION_RESOLVED`).
  - Cuando el admin crea un usuario (`USER_CREATED`) o le cambia el estado (`USER_STATUS_TOGGLED`)
  - Cuando alguien hace login (`AUTH_LOGIN`) o logout (`AUTH_LOGOUT`).
- Cada log guarda la fecha y hora exacta (`timestamp`), el usuario que hizo la acción (sacado directamente del `SecurityContextHolder`), la IP desde donde se hizo la petición (`request.getRemoteAddr()`) y una descripción de lo que se modificó.
- Hay un endpoint `GET /api/audit-logs` que devuelve los logs paginados y ordenados del más reciente al más viejo para que no pete la memoria si hay muchos registros.

---

## Modelo de Datos en PostgreSQL

Hemos estructurado la base de datos relacional en **4 tablas**:

```text
  +-------------------------+          +-------------------------+
  |         persons         |          |        reservas         |
  +-------------------------+          +-------------------------+
  | PK  id                  | 1      * | PK  id                  |
  | UQ  documento (DNI/Pas) |<---------| FK  passenger_id        |
  |     nombre              |          | UQ  identifier (AT-XXXX)|
  |     email               |          |     localizador (LOC-X) |
  |     edad                |          |     destino             |
  |     asiento             |          |     origin              |
  +-------------------------+          |     flight_number       |
                                       |     created_at          |
                                       +-------------------------+

  +-------------------------+          +-------------------------+
  |        app_users        |          |       audit_logs        |
  +-------------------------+          +-------------------------+
  | PK  id                  |          | PK  id                  |
  | UQ  username            |          |     timestamp           |
  |     password (hash)     |          |     username            |
  | UQ  email               |          |     action              |
  |     role                |          |     entity_type         |
  |     enabled             |          |     entity_id           |
  |     created_at          |          |     details             |
  +-------------------------+          |     ip_address          |
                                       +-------------------------+
```

1. **`persons`**: Guarda los datos personales del pasajero. El campo `documento` (DNI o pasaporte) tiene restricción `UNIQUE`, así que si subimos otro billete de la misma persona no la duplicamos en la tabla, sino que le vinculamos la reserva al id existente.
2. **`reservas`**: Guarda el vuelo, origen, destino, identificador (`AT-XXXX`) y localizador (`LOC-XXXX`). Tiene una relación `@ManyToOne` con clave foránea `passenger_id` apuntando a `persons`.
3. **`app_users`**: Los usuarios del sistema con su contraseña en hash bcrypt, su rol y el booleano enabled
4. **`audit_logs`**: Tabla donde se va acumulando el historial de auditoría para cumplir con la US3.

---

## Por qué usamos ORM (Spring Data JPA) en vez de SQL a mano (JDBC)

Para esta entrega se podía haber hecho todo metiendo sentencias SQL a mano con JDBC (`PreparedStatement`, `ResultSet`, etc.), pero decidimos usar un **ORM** con **Spring Data JPA e Hibernate** por varias razones:

1. **Menos código repetitivo (boilerplate)**: Con JDBC tradicional tienes que escribir a mano un montón de líneas para abrir conexiones, preparar la query, meter parámetros con `pstmt.setString(1, ...)` y luego parsear el resultado fila por fila con `rs.getString("nombre")`, `rs.getInt("edad")`, etc. Con JPA solo defines la entidad con anotaciones y el repositorio extiende de `JpaRepository`, ahorrándote cientos de líneas aburridas.
2. **Independencia del motor de base de datos**: Si metes SQL a pelo te atas a la sintaxis concreta de PostgreSQL. Con el ORM trabajas con objetos Java y el dialecto de Hibernate se encarga de traducir todo a la sintaxis adecuada. Si en el futuro cambias a MySQL o usas H2 para pruebas, no tienes que cambiar ni una sola línea de código
3. **Seguridad contra SQL Injection automática**: Al usar métodos de repositorio o consultas JPQL, Spring Data parametriza automáticamente todos los valores, con lo cual no hay peligro de que nadie te cuele una inyección SQL por el formulario.
4. **Manejo fácil de relaciones entre tablas**: En vez de hacer joins raros a mano y mapear dos objetos distintos desde un solo result set, con `@ManyToOne` y `@JoinColumn` le dices a JPA cómo están conectadas las tablas y el framework se encarga de traer y guardar los objetos relacionados de forma limpia.

---

### Cómo traduce Spring Data JPA a SQL en la práctica

Para entender qué hace el ORM por debajo, aquí dejo unos ejemplos reales de cómo nuestros métodos en Java se convierten en consultas SQL de PostgreSQL cuando se ejecutan:

#### 1. Buscar si hay colisiones de un pasajero (`findCollisions`)
En nuestro repositorio de reservas definimos esta consulta con JPQL:
```java
@Query("SELECT r FROM Reserva r WHERE r.passenger.nombre = :nombre AND r.origin = :origin AND r.destino = :destino")
List<Reserva> findCollisions(@Param("nombre") String nombre, @Param("origin") String origin, @Param("destino") String destino);
```
Cuando se ejecuta, Hibernate hace un `INNER JOIN` entre la tabla de reservas y la de personas pasando los tres parámetros:
```sql
SELECT r.id, r.identifier, r.localizador, r.destino, r.origin, r.flight_number, r.passenger_id, r.created_at
FROM reservas r
INNER JOIN persons p ON r.passenger_id = p.id
WHERE p.nombre = ? AND r.origin = ? AND r.destino = ?;
```

#### 2. Sacar las reservas de un destino ordenadas para meterlas en el ZIP
En el repositorio simplemente creamos la firma del método y Spring Data se encarga de inventar la query por el nombre:
```java
List<Reserva> findByDestinoOrderByIdAsc(String destino);
```
Por debajo, PostgreSQL ejecuta este SQL:
```sql
SELECT r.id, r.identifier, r.localizador, r.destino, r.origin, r.flight_number, r.passenger_id, r.created_at
FROM reservas r
WHERE r.destino = ?
ORDER BY r.id ASC;
```

#### 3. Comprobar si un pasajero ya existe por su DNI
En el repositorio de personas tenemos:
```java
Optional<Person> findByDocumento(String documento);
```
Que se traduce en una búsqueda simple filtrando por la clave única:
```sql
SELECT p.id, p.nombre, p.email, p.edad, p.documento, p.asiento
FROM persons p
WHERE p.documento = ?
LIMIT 1;
```

---

## Formato de los Datos CSV

### Formato de Entrada (el CSV que sube el usuario)
Debe tener exactamente estas 8 columnas:
```csv
destino,origin,flightNumber,nombre,edad,documento,asiento,email
```
Ejemplo:
```csv
ES,FR,KE3181,Daniel,34,00000001R,18B,daniel1@ycombinator.com
```

### Formato de Salida (el que se genera en el ZIP descargable)
El CSV generado tiene 10 columnas, añadiendo el identificador único y el localizador:
```csv
identifier,destino,origin,flightNumber,nombre,email,edad,documento,asiento,localizador
```
Ejemplo con colisión resuelta:
```csv
AT-0001,ES,FR,KE3181,Daniel,daniel1@ycombinator.com,34,00000001R,18B,LOC-0001
```

- **identifier**: Código único asignado secuencialmente a cada billete (`AT-%04d`).
- **localizador**:
    - Si el pasajero no coincide con nadie en nombre, origen y destino, la columna se queda vacía (`""`).
    - Si coinciden dos o más reservas en **Nombre, Origen y Destino**, comparten el mismo código de colisión (`LOC-%04d`).

---

## Tabla de Endpoints de la API

### 1. Autenticación (`/api/auth`)

| Método | Endpoint | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/login` | Público | Inicia sesión con usuario y pass y crea la cookie de sesión. |
| `GET` | `/api/auth/me` | Autenticado | Devuelve los datos del usuario que está conectado actualmente. |
| `POST` | `/api/auth/logout` | Autenticado | Cierra la sesión y limpia el contexto de seguridad. |

### 2. Administración de Usuarios (`/api/admin/users`)

| Método | Endpoint | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/users` | Solo `ADMIN` | Devuelve la lista de todos los usuarios registrados. |
| `POST` | `/api/admin/users` | Solo `ADMIN` | Registra un nuevo empleado o administrador en el sistema. |
| `PATCH` | `/api/admin/users/{id}/status` | Solo `ADMIN` | Bloquea o desbloquea a un usuario cambiando su flag `enabled`. |

### 3. Auditoría y Trazabilidad (`/api/audit-logs`)

| Método | Endpoint | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/audit-logs` | Autenticado | Devuelve los logs paginados (`page` y `size`) ordenados de más nuevo a más viejo. |
| `GET` | `/api/audit-logs/user/{username}` | Autenticado | Filtra los logs por el nombre de un usuario específico. |
| `GET` | `/api/audit-logs/action/{action}` | Autenticado | Filtra los logs según el tipo de acción (`RESERVA_CREATED`, etc.). |

### 4. Gestión de Reservas y Ficheros (`/files`)

| Método | Endpoint | Acceso | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/files/upload` | Autenticado | Sube un archivo CSV y guarda todas sus filas directamente en postgres. |
| `POST` | `/files/upload-line` | Autenticado | Ingiere una sola línea CSV en la base de datos controlando colisiones. |
| `GET` | `/files/zip` | Autenticado | Genera el fichero `AirTortilla.zip` con los CSVs particionados en memoria y lo descarga. |
| `GET` | `/files/db/reservas` | Autenticado | Devuelve todas las reservas guardadas en la base de datos. |
| `GET` | `/files/db/reservas/{identifier}` | Autenticado | Busca una reserva por su identificador único `AT-XXXX`. |
| `GET` | `/files/db/reservas/destino/{destino}` | Autenticado | Filtra reservas por el país/aeropuerto de destino. |
| `GET` | `/files/all` | Autenticado | Devuelve los nombres virtuales de los ficheros y cuántas reservas tienen. |
| `GET` | `/files/consult-file` | Autenticado | Paginación línea a línea para la vista interactiva del frontend. |

---

## Estructura del Proyecto

```text
FileSystem/
├── Dockerfile                    # Dockerfile multi-stage con temurin para compilar la app
├── docker-compose.yml            # Compose para levantar postgres y la app juntos
├── pom.xml                       # Dependencias maven (spring boot, jpa, security, lombok, postgres)
├── README.md                     # Esta documentacion
├── src/
│   ├── main/
│   │   ├── java/com/example/demo/
│   │   │   ├── config/           # DataInitializer (crea el admin por defecto al arrancar)
│   │   │   ├── controller/       # CsvController, AuthController, AdminUserController, AuditLogController
│   │   │   ├── dto/              # DTOs de peticiones y respuestas JSON
│   │   │   ├── exception/        # GlobalExceptionHandler (devuelve errores limpios en JSON)
│   │   │   ├── mapper/           # ReservasMapper (pasa de array de strings a entidad Reserva)
│   │   │   ├── model/            # Person, Reserva, User, Role, AuditLog (entidades con lombok)
│   │   │   ├── repository/       # Interfaces de Spring Data JPA
│   │   │   ├── security/         # SecurityConfig y CustomUserDetailsService
│   │   │   ├── service/          # Servicios de base de datos, usuario, auditoria, zip y csv
│   │   │   └── DemoApplication.java
│   │   └── resources/
│   │       ├── application.properties # Conexion a postgres y configuracion de jpa
│   │       ├── csv/                   # reservasAir.csv de ejemplo
│   │       └── static/                # Frontend web (index.html, app.js, style.css)
│   └── test/                          # DemoApplicationTests
└── images/
    └── diagrama_base.svg         # Diagrama de arquitectura
```

---

## Cómo ejecutar el proyecto

> ### Credenciales de Acceso por Defecto (SysAdmin)
> - **Usuario**: `admin`
> - **Contraseña**: `admin123`
> *(Al arrancar la app por primera vez, un seeder genera esta cuenta automaticamente en la base de datos para poder iniciar sesion de inmediato y probar todas las funciones de administracion, subida de archivos y auditoria).*

### Opción 1: Con Docker Compose (La forma recomendada)

Teniendo Docker Desktop arrancado en tu ordenador, solo tienes que situarte en la carpeta del proyecto y ejecutar:

```bash
docker compose up -d
```

Esto se encarga de todo automáticamente:
1. Descarga y arranca el contenedor de **PostgreSQL 16** con su volumen para no perder datos.
2. Descarga la imagen oficial desde Docker Hub [`xvirr/airtortilla:latest`](https://hub.docker.com/r/xvirr/airtortilla) (o la compila con el `Dockerfile` local si usas `--build`).
3. Espera a que PostgreSQL esté listo con un healthcheck y luego arranca la aplicación en el puerto `8080`.
4. Abre en tu navegador: [http://localhost:8080](http://localhost:8080)

Para pararlo cuando termines:
```bash
docker compose down
```

---

### Opción 2: Ejecutar en local con Maven

Si prefieres correrlo directamente en tu máquina sin Docker para la app, necesitas tener un PostgreSQL levantado en el puerto 5432 con base de datos `airtortilla`, usuario `airtortilla` y clave `airtortilla`:

1. **Compilar y arrancar la aplicación**:
   ```bash
   ./mvnw spring-boot:run
   ```

2. **Abrir en el navegador**:
   Entra a: [http://localhost:8080](http://localhost:8080)

3. **Cuenta para probar**:
   - **Usuario**: `admin`
   - **Contraseña**: `admin123`
   *(Con esta cuenta ya eres sysadmin y puedes probar la creación de usuarios, bloquear empleados, ver los logs de auditoría, subir CSVs y descargarte el ZIP).*
