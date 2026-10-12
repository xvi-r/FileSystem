package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
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

    public synchronized String generateId() throws IOException {
        long nextId = lastId + 1;

        Files.writeString(counterFile, String.valueOf(nextId));

        lastId = nextId;

        return "AT-%04d".formatted(nextId);
    }
}
