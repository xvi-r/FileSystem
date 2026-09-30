# FileSystem - Gestor de Reservas de Vuelos

Aplicación en Java desarrollada para el módulo de **Acceso a Datos (AAD)** que procesa información de reservas de vuelos desde un archivo CSV y las organiza en una estructura de carpetas y ficheros en el sistema de archivos utilizando Java NIO (`Files` y `Path`).

## Características

- **Procesamiento de CSV**: Lectura y parseo del archivo de datos `reservasAir.csv`.
- **Modelo de Dominio**: Mapeo de registros a objetos estructurados (`Reserva`, `Person`).
- **Generación de Ficheros**: Creación automática del árbol de directorios `Destinos/`:
  - Crea una carpeta por cada código de aeropuerto de destino (por ejemplo, `Destinos/MAD/`).
  - Genera un archivo `.txt` con los datos detallados del vuelo y pasajero nombrado con el número de vuelo (por ejemplo, `IB1234.txt`).

## Estructura del Proyecto

```
├── src/
│   ├── Main.java              # Clase principal: lee el CSV y genera carpetas/archivos
│   ├── Person.java            # Modelo que representa los datos del pasajero
│   ├── Reserva.java           # Modelo que representa los detalles de la reserva de vuelo
│   └── ReservasMapper.java    # Mapper de utilidad para convertir líneas de texto a Reserva
├── reservasAir.csv            # Archivo de entrada con los registros de reservas
├── .gitignore                 # Configurado para ignorar archivos del IDE, compilación y generados
└── README.md
```

## Requisitos

- **Java Development Kit (JDK)**: 17 o superior
- **IDE**: IntelliJ IDEA (o cualquier IDE de Java compatible)

## Instrucciones de Uso

1. Clona el repositorio:
   ```bash
   git clone https://github.com/xvi-r/FileSystem.git
   ```
2. Abre el proyecto en IntelliJ IDEA.
3. Ejecuta la clase `Main.java`.
4. Las carpetas organizadas por destino y los tickets de vuelo se generarán dentro del directorio `Destinos/`.
