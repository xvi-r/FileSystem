# AirTortilla — Sistema de Gestión y Partición de Reservas de Vuelo

Sistema integral desarrollado en **Java (Spring Boot)** e interfaz web interactiva para el procesamiento, partición y consulta de reservas de vuelos para la aerolínea **AirTortilla**, correspondiente al módulo de **Acceso a Datos (AAD)**.

---

## 📌 Visión General y Decisiones de Diseño

### ¿Qué se hizo y por qué?

El objetivo principal del proyecto es leer ficheros CSV de reservas, particionarlos en ficheros independientes organizados por país/aeropuerto de destino bajo la nomenclatura estándar `AirTortilla_XX_YYYY_MM_DD.csv`, e identificar de forma unívoca cada billete y agrupar mediante **localizadores** compartidos a aquellos pasajeros que coincidan en **Nombre, Origen y Destino**.

El enunciado requiere explícitamente que el sistema permita tanto la **modalidad total (en lote)** como la **modalidad línea a línea**, tanto para la generación como para la consulta.

---

### 💡 Reto Técnico y Arquitectura: Modo Línea a Línea vs Modo Lote

Procesar datos línea a línea plantea un reto de diseño sustancialmente más complejo que el procesamiento en lote:
- **En lote (Batch)**, el servidor tiene en memoria el fichero completo de antemano y puede predecir fácilmente qué pasajeros colisionan antes de escribir una sola línea.
- **Línea a línea**, cada fila llega como una petición HTTP aislada e independiente en el tiempo. Cuando llega el primer pasajero (por ejemplo, *Lucas* de *TGV* a *CIG*), el servidor no tiene forma de saber si diez minutos más tarde llegará un segundo *Lucas* con la misma ruta.

#### Solución implementada:
1. **`LocalizerService` con Búsqueda en $O(1)$**: 
   - Utiliza un `HashSet` en memoria (`seenCombinations`) para registrar cada combinación `NOMBRE#ORIGEN#DESTINO`.
   - Gracias al `HashSet`, la comprobación de pertenencia se realiza en **tiempo constante $O(1)$**, evitando escaneos lineales $O(N)$ sobre listas.
   - Si se detecta una colisión (segunda o enésima vez que aparece la combinación), genera o reutiliza un código secuencial persistente (`LOC-%04d`).

2. **Actualización Retroactiva de Ficheros (`fixCollisionInFile`)**:
   - Cuando llega una reserva que desencadena una colisión, el servicio abre el fichero de destino correspondiente mediante `java.nio.file.Files.readAllLines`.
   - Localiza la fila anterior perteneciente a ese mismo pasajero que tenía el localizador vacío y le asigna el código `LOC` generado.
   - Escribe el fichero actualizado de forma atómica y luego añade la nueva fila con el mismo código `LOC`.
   - **Ventaja**: Este mecanismo unifica ambos mundos; funciona de forma idéntica y transparente tanto si se envían líneas una a una por HTTP como si se procesa un fichero entero en bucle.

3. **`IdentifierService` (Identificación Inequívoca Persistente)**:
   - Cada reserva recibe un identificador único de billete (`AT-%04d`).
   - El contador se persiste en disco (`uploads/last-id.txt`) para garantizar que un reinicio de la aplicación no reinicie la numeración ni genere identificadores duplicados.
   - De forma análoga, el contador de localizadores se persiste en `uploads/last-loc-id.txt`.

4. **Gestión de Estado de Consulta en Memoria**:
   - En la interfaz web, el puntero de navegación línea a línea se gestiona en memoria y se reinicia al recargar la página (F5), evitando bloqueos o estados persistentes molestos para el usuario.

---

## 🛠️ Tecnologías Utilizadas

- **Backend**: Java 25 / Spring Boot 3.5 (Spring Web, SpringDoc OpenAPI / Swagger).
- **Entrada/Salida**: Java NIO (`Path`, `Paths`, `Files`, `Stream`, `StandardOpenOption`).
- **Frontend**: HTML5 semántico, CSS3 moderno (paleta oscura, variables CSS, diseño responsivo), Vanilla JavaScript.
- **Compresión**: `ZipOutputStream` en el backend y `JSZip` en el frontend para inspección en cliente sin recargas.

---

## 🌐 Documentación de Endpoints de la API (`/files`)

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

## 📂 Formato de Ficheros de Entrada y Salida

### Formato de Entrada (CSV subido)
Cabecera estricta de 8 columnas:
```csv
destino,origin,flightNumber,nombre,edad,documento,asiento,email
```

### Formato de Salida Generado
Ubicación: `uploads/reservas/{DESTINO}/AirTortilla_{DESTINO}_{YYYY}_{MM}_{DD}.csv`  
Cabecera de 10 columnas:
```csv
identifier,destino,origin,flightNumber,nombre,email,edad,documento,asiento,localizador
```
- Si el pasajero no tiene colisión: la columna `localizador` queda vacía (`""`).
- Si 2 o más pasajeros coinciden en Nombre, Origen y Destino: todos ellos comparten el mismo código `LOC-XXXX`.

---

## 🧪 Casos de Prueba con Colisiones

El repositorio incluye dos datasets preparados para verificar las colisiones exigidas por la especificación:

1. **[`reservas_tres_colisiones.csv`](reservas_tres_colisiones.csv)**: Contiene **exactamente los 3 casos de colisión**:
   - **Caso 1 (5 personas coincidentes)**: `Daniel` con ruta `BLH` &rarr; `GYI` (Vuelo KE3181)
   - **Caso 2 (2 personas coincidentes)**: `Sofia` con ruta `CCM` &rarr; `GID` (Vuelo NZ6574)
   - **Caso 3 (2 personas coincidentes)**: `Lucas` con ruta `TGV` &rarr; `CIG` (Vuelo QR1765)
2. **[`reservasAir.csv`](src/main/resources/csv/reservasAir.csv)**: Dataset completo de 57 registros con las mismas colisiones más pasajeros únicos.

---

## 🚀 Instrucciones de Ejecución

1. **Clonar el repositorio**:
   ```bash
   git clone https://github.com/xvi-r/FileSystem.git
   cd FileSystem
   ```

2. **Compilar y ejecutar la aplicación**:
   ```bash
   ./mvnw spring-boot:run
   ```

3. **Acceder a la interfaz web**:
   Abrir en el navegador: [http://localhost:8080](http://localhost:8080)
   - Desde la interfaz se puede alternar libremente entre la modalidad **Lote Completo** y **Línea a Línea** tanto para subir datos como para consultarlos.
   - Los registros con colisión se resaltan automáticamente en la tabla y en la ficha de detalle con su respectivo código `LOC`.
