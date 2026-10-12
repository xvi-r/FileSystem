package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
//me molestaba que el identificador hace un reset cada vez que reinicias la aplicacion porque el contador solo vive
//en memoria
//por esto este servicio sirve unicamente para incrementar y guardar el id
public class IdentifierService {

    private final Path counterFile =
            Path.of("uploads", "last-id.txt");

    private long lastId = 0;

    public IdentifierService() throws IOException {
        Files.createDirectories(counterFile.getParent());

        if (Files.exists(counterFile)) {
            lastId = Long.parseLong(
                    Files.readString(counterFile).trim()
            );
        }
    }

    //solo un hilo puede estar dentro de esta funcion ala vez para evitar problemas y inconsistencias al ser una operacion NO atomica
    public synchronized String generateId() throws IOException {
        long nextId = lastId + 1;

        Files.writeString(counterFile, String.valueOf(nextId));

        lastId = nextId;

        return "AT-%04d".formatted(nextId);
    }
}
