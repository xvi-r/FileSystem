package com.example.demo.service;

import com.example.demo.model.Reserva;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

@Service
//este servicio esta designado al parte del localizador del ejercicio
// aunque se podria usar el DNI (seria lo mas optimo) el ejercicio dice explicitamente que deberia crear un localizador
//si existen el mismo nombre, destino y origen, aprate de esto era una manera interesenante de aprender sobre notacion Big O
//ya que mi primer iteracion tenia una posibilidad de llegar a O(N^2) que seria super in-optimo
// el reto en si es que al procesar de manera verdadera linea por linea no puedes pasar el archivo directamente a memoria
// si un usuario solo lee 20% y para vas a tener problemas con colisiones que ni existen
public class LocalizerService {
    // HASHMAP O(1) vs LISTA O(N): usamos un HashMap en vez de un Set porque necesitamos
    // guardar el byteOffset (Long) de la primera aparicion en disco.
    // Esto nos da comprobacion en O(1) amortizado y si hay colision sabemos exactamente 
    // a que byte del archivo saltar con seek() para inyectr el LOC sin reescribir todo el fichero.
    public final Map<String,Long> seenCombinations = new HashMap<>();
    private final Path locCounterFile = Path.of("uploads", "last-loc-id.txt");
    private long lastLocId = 0;

    //si existe una colision lo ponemos aqui con un LOC
    private final Map<String, String> localizadores = new HashMap<>();

     // Al instanciar el servicio, cargamos el ultimo contador de localizador persistido en disco
    // para garantizar consistencia entre reinicios del servidor
    public LocalizerService() throws IOException {
        Files.createDirectories(locCounterFile.getParent());
        // Cargar el último ID persistido si existe
        if (Files.exists(locCounterFile)) {
            lastLocId = Long.parseLong(Files.readString(locCounterFile).trim());
        }
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

    //aunque no uso el booleano lo voy a dejar ahi porque puede ser muy util para otras funcionalidades
    public boolean addComboIfNotExists(Reserva reserva, Path file) throws IOException {
        String combo = buildCombo(reserva.getPassenger().getNombre(), reserva.getOrigin(), reserva.getDestino());
        Long comboByteOffset = file.toFile().length() - 11;

        if(!seenCombinations.containsKey(combo)) {
            seenCombinations.put(combo, comboByteOffset);

            String loc = generateLocCode();
            localizadores.put(combo,loc);

            return false;
        } else {
            //solo a la primera iteracion tenemos que parchear la linea corespondiente despues ya no hace falta
            if(seenCombinations.get(combo) != null)
            {
                //parcheamos la linea
                patchLine(file, seenCombinations.get(combo), combo);
                seenCombinations.put(combo,null);
            }

            //y actualizamos la linea que acabamos de escribir
            updateLastWrittenLine(file, localizadores.get(combo), combo);
            return true;
        }
    }

    private void patchLine(Path file, Long comboByteOffset, String combo) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
            raf.seek(seenCombinations.get(combo));

            raf.write(localizadores.get(combo).getBytes(StandardCharsets.UTF_8));
        }
    }

    public void updateLastWrittenLine(Path file, String loc, String combo) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {

            raf.seek(file.toFile().length() - 11);
            raf.write(localizadores.get(combo).getBytes(StandardCharsets.UTF_8));
        }
    }
}
