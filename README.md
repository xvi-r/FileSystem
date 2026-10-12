# AirTortilla — Sistema de Gestión y Partición de Reservas de Vuelo

Sistema integral desarrollado en **Java (Spring Boot)** e interfaz web interactiva para el procesamiento, partición y consulta de reservas de vuelos para la aerolínea **AirTortilla**, correspondiente al módulo de **Acceso a Datos (AAD)**.

---

## Formato y Estructura de Ficheros CSV

### 1. Formato de Entrada (CSV subido por el usuario)
Cabecera requerida de 8 columnas:
```csv
destino,origin,flightNumber,nombre,edad,documento,asiento,email
```

Ejemplo de línea de entrada:
```csv
GYI,BLH,KE3181,Daniel,57,59708064I,14B,Daniel_59708064I@gmail.com
```

### 2. Formato de Salida Generado
Ubicación en disco: `uploads/reservas/{DESTINO}/AirTortilla_{DESTINO}_{YYYY}_{MM}_{DD}.csv`  
Cabecera estandarizada de 10 columnas:
```csv
identifier,destino,origin,flightNumber,nombre,email,edad,documento,asiento,localizador
```

Ejemplo de línea generada con colisión:
```csv
AT-0001,GYI,BLH,KE3181,Daniel,Daniel_59708064I@gmail.com,57,59708064I,14B,LOC-0001
```

- **identifier**: Identificador único e inequívoco asignado secuencialmente a cada billete (`AT-%04d`).
- **localizador**: 
  - Si el pasajero no tiene colisión con nadie (combinación Nombre + Origen + Destino única), la columna queda vacía (`""`).
  - Si dos o más reservas coinciden en **Nombre, Origen y Destino**, comparten el mismo código identificador de colisión (`LOC-%04d`).

---

## Elección Tecnológica: ¿Por qué Spring Boot?

Se eligió **Spring Boot** como base del proyecto por las siguientes razones:

1. **Visión a largo plazo y escalabilidad**: Este proyecto representa una base de trabajo que se irá extendiendo a lo largo del tiempo. Spring Boot proporciona un ecosistema robusto y desacoplado que facilita enormemente añadir nuevas funcionalidades o cambiar especificaciones sobre la marcha sin reescribir la aplicación.
2. **Facilidad de evolución (p. ej. migración a JSON/REST)**: Si en futuras fases de la asignatura se requiere transformar las respuestas o la persistencia a JSON, bases de datos relacionales/NoSQL o exponer APIs REST más complejas, Spring Boot permite realizar estas transiciones de manera directa mediante anotaciones y serializadores listos para usar.
3. **Afinidad con el ecosistema Java**: Es un framework muy interesante y entretenido para aprender en profundidad, aprovechando al máximo las capacidades del lenguaje Java.

---

## Visión General y Decisiones de Diseño

### ¿Qué se hizo y por qué?

El objetivo principal del proyecto es leer ficheros CSV de reservas, particionarlos en ficheros independientes organizados por país/aeropuerto de destino bajo la nomenclatura estándar `AirTortilla_XX_YYYY_MM_DD.csv`, identificar de forma unívoca cada billete y agrupar mediante **localizadores** compartidos a aquellos pasajeros que coincidan en **Nombre, Origen y Destino**.

El enunciado exige que el sistema admita tanto la **modalidad total (en lote)** como la **modalidad línea a línea**, tanto para la generación como para la consulta.

---

### Reto Técnico: Modo Línea a Línea vs Modo Lote

Procesar datos línea a línea plantea un reto de diseño sustancialmente más complejo que el procesamiento en lote:
- **En lote (Batch)**: El servidor tiene en memoria el fichero completo de antemano y puede predecir fácilmente qué pasajeros colisionan antes de escribir una sola línea en disco.
- **Línea a línea**: Cada fila llega como una petición HTTP aislada e independiente en el tiempo. Cuando llega el primer pasajero (por ejemplo, *Lucas* de *TGV* a *CIG*), el servidor no tiene forma de saber si minutos o peticiones más tarde llegará un segundo *Lucas* con la misma ruta.

#### Solucion implementada y Optimizacion Notacion Big O:

1. **`LocalizerService` con Busqueda e Insercion en O(1)**:
   - Utiliza una estructura hash en memoria (`Map<String, Long> seenCombinations`) para registrar cada combinacion `NOMBRE#ORIGEN#DESTINO`.
   - La comprobacion de pertenencia se realiza en **tiempo constante O(1)** amortizado.

2. **El Reto Tecnico de Big O y Solucion con Byte Seeking in situ**:
   - **El Problema Naive (O(N^2))**: En la primera iteracion, cuando un segundo pasajero causaba una colision, el servidor abria el fichero con `Files.readAllLines()`, leia todas las lineas a memoria y reescribia el archivo completo en disco para anadir el `LOC` a la primera reserva. Para un archivo de $M$ lineas procesando $N$ reservas, esto implicaba un coste de E/S de disco de $O(M)$ por colision, escalando en el peor caso a **$O(N \cdot M) \approx O(N^2)$**, con una amplificacion de escritura enorme.
   - **La Solucion Optima (O(1) estricto)**:
     - Cada fila reserva preventivamente un espacio fijo de 10 bytes para el localizador (`"          "`).
     - Al escribir la primera reserva de una ruta, el servicio indexa en memoria su posicion exacta en bytes (`comboByteOffset = file.length() - 11`).
     - Al llegar la colision, en lugar de reescribir el archivo, se utiliza `RandomAccessFile.seek(byteOffset)`.
     - El cursor se posiciona directamente sobre los 10 bytes reservados y sobrescribe **unicamente esos 10 bytes** con el codigo `LOC-XXXX` generado.
     - **Complejidad resultante**: El coste de disco e I/O pasa de $O(M)$ a **$O(1)$ directo**, eliminando la necesidad de leer o reescribir ficheros y manteniendo el procesamiento linea a linea en tiempo lineal optimo global $O(N)$.

3. **`IdentifierService` (Identificacion Inequivoca Persistente)**:
   - Cada reserva recibe un identificador unico de billete (`AT-%04d`).
   - El contador se persiste en disco (`uploads/last-id.txt`) para garantizar que un reinicio de la aplicacion no reinicie la numeracion ni genere identificadores duplicados.
   - De forma analoga, el contador de localizadores se persiste en `uploads/last-loc-id.txt`.

4. **Gestion de Estado de Consulta en Memoria**:
   - En la interfaz web, el puntero de navegacion linea a linea se gestiona en memoria y se reinicia al recargar la pagina (F5), evitando bloqueos o estados persistentes para el usuario.

---

## Tecnologías Utilizadas

- **Backend**: Java 25 / Spring Boot 3.5 (Spring Web, SpringDoc OpenAPI / Swagger).
- **Entrada/Salida**: Java NIO (`Path`, `Paths`, `Files`, `Stream`, `StandardOpenOption`).
- **Frontend**: HTML5 semántico, CSS3 moderno (paleta oscura, variables CSS, diseño responsivo), Vanilla JavaScript.
- **Compresión**: `ZipOutputStream` en el backend y `JSZip` en el frontend para inspección en cliente sin recargas.

---

## Documentación de Endpoints de la API (`/files`)

Todos los endpoints están expuestos bajo el controlador `CsvController` con la ruta base `/files`:

### 1. Generación y Procesamiento de Reservas

| Método | Endpoint | Consumes / Parámetros | Descripción |
| :--- | :--- | :--- | :--- |
| `POST` | `/files/upload-csv` | `multipart/form-data` (`file`) | Sube un archivo CSV completo y lo procesa en lote, particionándolo en las carpetas de destino. |
| `POST` | `/files/line` | `text/csv` (Cuerpo de texto) | Procesa y añade una única línea CSV al archivo correspondiente a su destino, aplicando detección de colisiones. |
| `GET` | `/files/process-csv` | Ninguno | Reprocesa el archivo previamente subido a `uploads/baseCSV/uploadedCSV`. |
| `GET` | `/files/generate-reservas` | Ninguno | Carga y genera las reservas iniciales desde el recurso `csv/reservasAir.csv`. |

### 2. Consulta y Recuperación

| Método | Endpoint | Respuesta | Descripción |
| :--- | :--- | :--- | :--- |
| `GET` | `/files/zip` | `application/zip` (binario) | Empaqueta todos los ficheros particionados de `uploads/reservas/` en un archivo `AirTortilla.zip` descargable. |
| `GET` | `/files/consult/files` | `application/json` (`Map<String, Integer>`) | Devuelve un mapa con el nombre de cada fichero CSV generado y su número de reservas. |
| `POST` | `/files/consult/line/initialize` | `text/plain` | Lee todos los ficheros existentes en disco e inicializa las estructuras en memoria para la navegación línea a línea. |
| `GET` | `/files/consult/line` | `text/csv` (`file`, `index`) | Devuelve la línea CSV correspondiente al índice indicado para un fichero específico o para `"all"`. Retorna `HTTP 204 No Content` si se alcanza el final. |
| `GET` | `/files/reservas` | `text/plain` | Endpoint de diagnóstico que devuelve el total de reservas cargadas en la lista en memoria. |

---

## Casos de Prueba con Colisiones

El repositorio incluye datasets preparados para verificar las colisiones exigidas por la especificacion:

1. **[`reservas_tres_colisiones.csv`](reservas_tres_colisiones.csv)**: Contiene **exactamente los 3 casos de colision** con codigos ISO de 2 letras:
   - **Caso 1 (5 personas coincidentes)**: `Daniel` con ruta `FR` -> `ES` (Vuelo KE3181)
   - **Caso 2 (2 personas coincidentes)**: `Sofia` con ruta `DE` -> `FR` (Vuelo NZ6574)
   - **Caso 3 (2 personas coincidentes)**: `Lucas` con ruta `UK` -> `IT` (Vuelo QR1765)
2. **[`reservas_colisiones_nuevas.csv`](reservas_colisiones_nuevas.csv)**: Dataset alternativo con colisiones de Alejandro (x5), Elena (x2) y Marco (x2).
3. **[`reservas_100_con_colisiones.csv`](reservas_100_con_colisiones.csv)**: Dataset extenso de 100 reservas con colisiones y vuelos independientes.
4. **[`reservasAir.csv`](src/main/resources/csv/reservasAir.csv)**: Dataset base del sistema con 57 registros.

---

## Instrucciones de Ejecucion

### Opcion A: Ejecucion Rapida con Docker (Imagen Publica en Docker Hub)

La imagen oficial ya incluye el codigo compilado, la estructura de directorios y los ficheros CSV de prueba en `/app/mock_data/`:

```bash
docker run -d -p 8080:8080 -v $(pwd)/uploads:/app/uploads --name airtortilla-app xvirr/airtortilla:latest
```

O usando `docker compose`:
```bash
docker compose up -d
```

### Opcion B: Ejecucion Local con Maven Wrapper

1. **Clonar el repositorio**:
   ```bash
   git clone https://github.com/xvi-r/FileSystem.git
   cd FileSystem
   ```

2. **Compilar y ejecutar la aplicacion**:
   ```bash
   ./mvnw spring-boot:run
   ```

3. **Acceder a la interfaz web**:
   Abrir en el navegador: [http://localhost:8080](http://localhost:8080)
   - Desde la interfaz se puede alternar libremente entre la modalidad **Lote Completo** y **Linea a Linea** tanto para subir datos como para consultarlos.
   - Los registros con colision se resaltan visualmente en la tabla y en la ficha de detalle con su respectivo codigo `LOC`.
