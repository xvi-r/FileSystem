# FileSystem - Flight Reservations Manager

A Java application developed for **Acceso a Datos (AAD)** that reads flight reservation data from a CSV file and organizes it into a hierarchical file system structure using Java NIO (`Files` and `Path`).

## Features

- **CSV Parsing**: Reads and parses flight reservation records from `reservasAir.csv`.
- **Data Modeling**: Maps raw records to structured domain models (`Reserva`, `Person`).
- **File System Generation**: Automatically generates a `Destinos/` directory structure:
  - Creates a dedicated folder for each destination airport code (e.g., `Destinos/MAD/`).
  - Generates a formatted reservation summary file named after the flight number (e.g., `IB1234.txt`).

## Project Structure

```
├── src/
│   ├── Main.java              # Entry point: reads CSV and generates the destination folders/files
│   ├── Person.java            # Model representing passenger information
│   ├── Reserva.java           # Model representing flight reservation details
│   └── ReservasMapper.java    # Utility mapper converting CSV tokens to Reserva objects
├── reservasAir.csv            # Input dataset containing reservation records
├── .gitignore                 # Configured to ignore IDE, build, and generated directories
└── README.md
```

## Requirements

- **Java Development Kit (JDK)**: 17 or higher
- **IDE**: IntelliJ IDEA (or any Java IDE)

## Getting Started

1. Clone the repository:
   ```bash
   git clone https://github.com/xvi-r/FileSystem.git
   ```
2. Open the project in IntelliJ IDEA.
3. Run `Main.java`.
4. The generated folders and flight ticket files will appear inside the `Destinos/` directory.
