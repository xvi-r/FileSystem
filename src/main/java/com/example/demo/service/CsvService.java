package com.example.demo.service;

import com.example.demo.mapper.ReservasMapper;
import com.example.demo.model.Reserva;
import org.springframework.stereotype.Service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;

import java.nio.file.*;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import java.util.stream.Stream;

@Service
public class CsvService {
    private final ArrayList<Reserva> reservaList = new ArrayList<>();
    private  Map<String, List<String>> csvReservasMap;
    private List<String> reservasGeneradas;

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

            Path destinoFolder = Path.of("uploads", "reservas", reserva.getDestino());
            Files.createDirectories(destinoFolder);

            String fileName = buildCsvFileName(reserva);

            Path file = destinoFolder.resolve(fileName);

            boolean fileExists = Files.exists(file);

            try (BufferedWriter writer = Files.newBufferedWriter(
                    file,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {

                if(!fileExists) {
                    writer.write("identifier,destino,origin,flightNumber,nombre,email,edad,documento,asiento\n");
                }

                Reserva.setIdentifier(Reserva.getIdentifier() + 1);
                writer.write(reserva.getCsv());
            }

        }
    }

    private String writeCsv(Reserva reserva) {
        Reserva.setIdentifier(Reserva.getIdentifier() + 1);
        return "identifier,destino,origin,flightNumber,nombre,email,edad,documento,asiento\n" +
                reserva.getCsv();
    }

    public void processLine(String csvLine) throws IOException {
        Reserva reserva = ReservasMapper.stringArrayToReserva(csvLine.split(","));
        String destino = reserva.getDestino().toUpperCase(Locale.ROOT);

        Path directory = Paths.get("uploads", "reservas", destino);
        Files.createDirectories(directory);

        Path csvPath = directory.resolve(buildCsvFileName(reserva));

        boolean fileExists = Files.exists(csvPath);

        //Usando CREATE se crea el fichero si no existe
        try (BufferedWriter writer = Files.newBufferedWriter(
                csvPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {

            if (!fileExists) {
                writer.write("identifier,destino,origin,flightNumber,nombre,email,edad,documento,asiento\n");
            }

            Reserva.setIdentifier(Reserva.getIdentifier() + 1);
            writer.write(reserva.getCsv());
        }

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

    public String consultLine(String csv, int index) throws IOException {
        if(csv.equals("all")) {
            return reservasGeneradas.get(index);
        } else {
            return csvReservasMap.get(csv).get(index);
        }
    }



    //La idea de esta funcion es crear un Map donde cada clave es el Fichero y su valor una lista de las reservas correspondiente
    //para las consultas linea a linea es util porque podemos ir por indices y devolver la line corespondiente
    public void initLineByLineReading() throws IOException {
        Map<String, List<String>> csvReservasMap = new HashMap<>();

        List<Path> csvs;

        try(Stream<Path> paths = Files.walk(Path.of("uploads", "reservas"))){
           csvs = paths.filter(Files::isRegularFile).toList();
        };

        for(Path csv : csvs) {
            ArrayList<String> reservas = new ArrayList<>();

            try (BufferedReader reader = Files.newBufferedReader(csv)) {
                String line = reader.readLine(); // Saltar el Header

                while ((line = reader.readLine()) != null) {
                    reservas.add(line.strip());
                }
            }

            csvReservasMap.put(csv.getFileName().toString(), reservas);

        }
        this.csvReservasMap = csvReservasMap;
        // en el caso de linea por linea donde quieren consultar todas en vez de un fichero especifico
        this.reservasGeneradas = csvReservasMap.values().stream().flatMap(List::stream).toList();
    }

    //esta funcion al usar json es muy necesaria para que el frontend sabe cuales ficheros existen para las consultas
    //linea en linea y cuantos reservas tiene cada uno
    public Map<String,Integer> getAllCsvNames() throws IOException {
        Path directory = Path.of("uploads", "reservas");
        Map<String,Integer> csvsRowCountMap = new HashMap<>();

        //simplemente busca todos los ficheros csv y lo devuelve en una lista
        List<Path> csvs;
        try (Stream<Path> paths = Files.walk(directory)) {
            csvs = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".csv"))
                    .sorted()
                    .toList();
        }


       for(Path csv : csvs) {
           Integer reservasCount = 0;
           try (BufferedReader reader = Files.newBufferedReader(csv)) {
               String line = reader.readLine(); // Saltar el Header

               while ((line = reader.readLine()) != null) {
                    reservasCount++;
               }
           }
           csvsRowCountMap.put(csv.getFileName().toString(),reservasCount);
       }
       return csvsRowCountMap;
    }
}



