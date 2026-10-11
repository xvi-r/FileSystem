package com.example.demo.service;

import com.example.demo.mapper.ReservasMapper;
import com.example.demo.model.Reserva;
import org.springframework.stereotype.Service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.nio.file.*;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CsvService {
    private final ArrayList<Reserva> reservaList = new ArrayList<>();

    public String test() {
        return "Found " + reservaList.size();
    }

    public void generateReservasFromCsv() {
        Resource resource = new ClassPathResource("csv/reservasAir.csv");

        try (BufferedReader reader =
                     new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            String line = reader.readLine();

            while ((line = reader.readLine()) != null) {
                reservaList.add(ReservasMapper.stringArrayToReserva(line.split(",")));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void storeCsv(MultipartFile file) throws IOException {
        Path uploadDirectory = Paths.get("uploads", "baseCSV");

        Path filePath = uploadDirectory.resolve("uploadedCSV");
        System.out.println("Resolved path: " + filePath.toAbsolutePath());
        System.out.println("Exists: " + Files.exists(filePath));

        Files.copy(
                file.getInputStream(),
                filePath,
                StandardCopyOption.REPLACE_EXISTING
        );
    }

    public void processCsv() throws IOException {
        System.out.println("ENTERED");
        Path directory = Path.of("uploads/reservas");
        Path uploadedFileDirectory = Path.of("uploads", "baseCSV", "uploadedCSV");

        try (BufferedReader reader = Files.newBufferedReader(uploadedFileDirectory)) {
            String line = reader.readLine();

            while ((line = reader.readLine()) != null) {
                reservaList.add(ReservasMapper.stringArrayToReserva(line.split(",")));
            }
        }

        for (Reserva reserva : reservaList) {
            List<Path> fold = Files.list(directory).toList();

            Path destinoFolder = Path.of("uploads", "reservas", reserva.getDestino());
            Files.createDirectories(destinoFolder);

            String fileName = buildCsvFileName(reserva);

            Path file = destinoFolder.resolve(fileName);
            if (Files.exists(file)) {
                System.out.println("entered");
                Reserva.setIdentifier(Reserva.getIdentifier() + 1);
                Files.write(
                        file,
                        reserva.getCsv().getBytes(),
                        StandardOpenOption.APPEND
                );
            } else {
                try {
                    Files.createFile(file);
                    Files.writeString(file, writeCsv(reserva));
                } catch (FileAlreadyExistsException e) {
                    System.out.println("appending");
                }
            }
        }
    }

    private String writeCsv(Reserva reserva) {
        Reserva.setIdentifier(Reserva.getIdentifier() + 1);
        return "identifier,destino,origin,flightNumber,nombre,email,edad,documento,asiento\n" +
                reserva.getCsv();
    }

    public void processLine(String csvLine) {
        Reserva reserva = ReservasMapper.stringArrayToReserva(csvLine.split(","));
        String destino = reserva.getDestino().toUpperCase(Locale.ROOT);

        Path csvPath = Paths.get("uploads", "reservas", destino);
    }

    private String buildCsvFileName(Reserva reserva) {
        LocalDate today = LocalDate.now();

        int year = today.getYear();
        String month = String.format("%02d", today.getMonthValue());
        String day = String.format("%02d", today.getDayOfMonth());

        String destinationCountryCode = reserva.getDestino();

        // Construir el nombre del fichero según el requisito: AirTortilla_XX_YYYY_MM_DD
        return String.format("AirTortilla_%s_%d_%s_%s.csv", destinationCountryCode, year, month, day);
    }
}
