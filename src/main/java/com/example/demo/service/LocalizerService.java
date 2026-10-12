package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

@Service
//este servicio esta designado al parte del localizador del ejercicio
public class LocalizerService {
    //SET O(1) vs O(N) un set es muchooo mas rapido que una lista y siempre nos va a dar O(1) en este caso
    //muy importante porque para tener un localizador propio vamos a tener que verificar cada escritura
    public final Set<String> seenCombinations = new HashSet<>();
    private final Path locCounterFile = Path.of("uploads", "last-loc-id.txt");
    private long lastLocId = 0;

    //si existe una colision lo ponemos aqui con un LOC
    private final Map<String, String> localizadores = new HashMap<>();

    //cuando spring crea este bean genera las combinaciones ya vistas
    public LocalizerService() throws IOException {
        Files.createDirectories(locCounterFile.getParent());
        // Cargar el último ID persistido si existe
        if (Files.exists(locCounterFile)) {
            lastLocId = Long.parseLong(Files.readString(locCounterFile).trim());
        }

        loadSeenCombinations();
    }

    public String buildCombo(String nombre, String origin, String destino) {
        return (nombre.trim() + "#" + origin.trim() + "#" + destino.trim()).toUpperCase();
    }

    public List<Path> getAllCsvFiles() throws IOException {

        Path directory = Paths.get("uploads", "reservas");

        if (!Files.exists(directory)) {
            return List.of();
        }

        List<Path> allCsvFiles;

        try (Stream<Path> stream = Files.walk(directory)) {
            allCsvFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".csv"))
                    .toList();
        }
        return allCsvFiles;
    }

    private synchronized String generateLocCode() throws IOException {
        long nextId = lastLocId + 1;
        Files.writeString(locCounterFile, String.valueOf(nextId));
        lastLocId = nextId;
        return "LOC-%04d".formatted(nextId);
    }

    //al empezar tenemos que popular el set con estos datos
    public void loadSeenCombinations() throws IOException {
        for (Path file : getAllCsvFiles()) {
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                String line = reader.readLine(); // Saltar header como siempre

                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;

                    String[] parts = line.split(",");
                    String destino = parts[1].trim();
                    String origin = parts[2].trim();
                    String nombre = parts[4].trim();

                    // creamos la combinacion para el hashet
                    String combo = buildCombo(nombre,origin,destino);
                    seenCombinations.add(combo);
                }
            }
        }
    }

    public synchronized String processAndGetLocalizador(String nombre, String origin, String destino) throws IOException {
        String combo = (nombre.trim() + "#" + origin.trim() + "#" + destino.trim()).toUpperCase();
        if (seenCombinations.contains(combo)) {
            // colision; asignamos o reutilizamos el código
            if (!localizadores.containsKey(combo)) {
                localizadores.put(combo, generateLocCode());
            }
            return localizadores.get(combo);
        } else {
            // Primera vez que vemos esta combinacion
            seenCombinations.add(combo);
            return "";
        }
    }
}

