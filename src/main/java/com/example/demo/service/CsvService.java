package com.example.demo.service;

import com.example.demo.mapper.ReservasMapper;
import com.example.demo.model.Reserva;
import com.example.demo.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsvService {

    private final ReservaDatabaseService reservaDatabaseService;
    private final ReservaRepository reservaRepository;

    public String test() {
        return "Found " + reservaDatabaseService.count();
    }

    // genera unas cuantas reservas de prueba del csv de ejemplo directamente a postgres
    public void generateReservasFromCsv() {
        Resource resource = new ClassPathResource("csv/reservasAir.csv");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.getInputStream()))) {
            String line = reader.readLine(); //saltamos la cabecera

            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                Reserva reserva = ReservasMapper.stringArrayToReserva(line.split(","));
                reservaDatabaseService.saveOrUpdate(reserva);
                count++;
            }
            log.info("saved {} sample reservations into database", count);
        } catch (Exception e) {
            throw new RuntimeException("failed to generate sample reservations from csv", e);
        }
    }

    // procesamos el csv subido linea a linea directo a la bd sin tocar disco
    public int processUploadedCsv(MultipartFile file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line = reader.readLine(); // saltamos cabecera
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                Reserva reserva = ReservasMapper.stringArrayToReserva(line.split(","));
                reservaDatabaseService.saveOrUpdate(reserva);
                count++;
            }
            log.info("processed and saved {} reservations from csv into database", count);
            return count;
        }
    }

    public void storeCsv(MultipartFile file) throws IOException {
        processUploadedCsv(file);
    }

    public void processCsv() throws IOException {
        //ya no hace falta pq se guarda en memoria al vuelo
    }

    public void processLine(String csvLine) {
        if (csvLine == null || csvLine.isBlank()) return;
        Reserva reserva = ReservasMapper.stringArrayToReserva(csvLine.split(","));
        reservaDatabaseService.saveOrUpdate(reserva);
    }

    // consulta linea a linea pero tirando de paginacion sql en vez de cargar tocho en memoria
    public String consultLine(String csvFile, int index) {
        PageRequest pageRequest = PageRequest.of(index, 1, Sort.by("id").ascending());
        Page<Reserva> page;

        if (csvFile == null || csvFile.equalsIgnoreCase("all")) {
            page = reservaRepository.findAll(pageRequest);
        } else {
            String destino = extractDestinoFromFileName(csvFile);
            page = reservaRepository.findByDestino(destino, pageRequest);
        }

        if (page.isEmpty()) {
            return null;
        }

        return page.getContent().get(0).getCsv().trim();
    }

    public void initLineByLineReading() {
        //no hace falta nada pq la bd ya esta indexada
    }

    // saca los nombres de ficheros virtuales y cuantas filas tienen consultando la bd
    public Map<String, Integer> getAllCsvNames() {
        Map<String, Integer> result = new LinkedHashMap<>();
        List<String> destinos = reservaRepository.findDistinctDestinos();
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));

        for (String destino : destinos) {
            long count = reservaRepository.countByDestino(destino);
            String fileName = "AirTortilla_%s_%s.csv".formatted(destino, dateStr);
            result.put(fileName, (int) count);
        }

        return result;
    }

    private String extractDestinoFromFileName(String fileName) {
        if (fileName.startsWith("AirTortilla_")) {
            String[] parts = fileName.split("_");
            if (parts.length >= 2) {
                return parts[1];
            }
        }
        return fileName.replace(".csv", "").trim();
    }
}
