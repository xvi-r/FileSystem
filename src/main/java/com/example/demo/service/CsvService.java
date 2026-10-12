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
    private final IdentifierService identifierService;
    private final LocalizerService localizerService;
    private final ArrayList<Reserva> reservaList = new ArrayList<>();
    private  Map<String, List<String>> csvReservasMap;
    private List<String> reservasGeneradas;

    public CsvService(IdentifierService identifierService, LocalizerService localizerService) {
        this.identifierService = identifierService;
        this.localizerService = localizerService;
    }

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

        reservaList.clear();

        try (BufferedReader reader = Files.newBufferedReader(uploadedFileDirectory)) {
            String line = reader.readLine();

            while ((line = reader.readLine()) != null) {
                reservaList.add(ReservasMapper.stringArrayToReserva(line.split(",")));
            }
        }

        for (Reserva reserva : reservaList) {
            appendReservaToFile(reserva);
        }
    }

    public void processLine(String csvLine) throws IOException {
        Reserva reserva = ReservasMapper.stringArrayToReserva(csvLine.split(","));
        appendReservaToFile(reserva);
    }

    private void appendReservaToFile(Reserva reserva) throws IOException {
        String destino = reserva.getDestino().toUpperCase(Locale.ROOT);
        Path destinoFolder = Path.of("uploads", "reservas", destino);
        Files.createDirectories(destinoFolder);

        String fileName = buildCsvFileName(reserva);
        Path file = destinoFolder.resolve(fileName);

        reserva.setIdentifier(identifierService.generateId());
        //aplicamos el localizador
//        String loc = localizerService.processAndGetLocalizador(
//                reserva.getPassenger().getNombre(),
//                reserva.getOrigin(),
//                reserva.getDestino()
//        );
//        reserva.setLocalizador(loc);

        // Si se detecta colisión, actualizamos las líneas anteriores en este fichero que quedaron con el LOC vacío
//        if (!loc.isEmpty()) {
//            fixCollisionInFile(file, reserva.getPassenger().getNombre(), reserva.getOrigin(), reserva.getDestino(), loc);
//
//        }

        boolean fileExists = Files.exists(file);

        try (BufferedWriter writer = Files.newBufferedWriter(
                file,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {

            if (!fileExists) {
                writer.write("identifier,destino,origin,flightNumber,nombre,email,edad,documento,asiento,localizador\n");
            }

            writer.write(reserva.getCsv());
            writer.flush();
            localizerService.addComboIfNotExists(reserva,file);


        }
    }

    //ACTUALIZACION ESTO ERA LA PRIMERA ITERACION!! QUE ME DABA PROBLEMAS DE O(N^2)
    //cuando llegamos a encontrar una colision tenemos que volver al fichero y cambiar la linea que no tiene su loc
    //porque la primera vez que se deteca una colision es porque YA existe una reserva con estos datos tenemos que volver
    //y actualizar lo, subir los ficheros solo en modo batch lo haria un poco mas facil pero el ejercicio
    //pide que tambien tiene soporte para subir los ficheros en modalidad linea por linea
//    private void fixCollisionInFile(Path file, String nombre, String origin, String destino, String loc) throws IOException {
//        if (!Files.exists(file)) return;
//
//        //parece que existe tambien esta manera de leer los ficheros que parece super rapido y sencillo
//        List<String> lines = Files.readAllLines(file);
//        List<String> updatedLines = new ArrayList<>(lines.size());
//        String targetCombo = localizerService.buildCombo(nombre, origin, destino);
//
//        for (String line : lines) {
//            // Ignorar la cabecera CSV o lineas en blanco
//            if (line.isBlank() || line.startsWith("identifier")) {
//                updatedLines.add(line);
//                continue;
//            }
//
//            //generamos el combo
//            ArrayList<String> parts = new ArrayList<>(Arrays.asList(line.split(",", -1)));
//            String fileDestino = parts.get(1);
//            String fileOrigin = parts.get(2);
//            String fileNombre = parts.get(4);
//
//            String lineCombo = localizerService.buildCombo(fileNombre, fileOrigin, fileDestino);
//
//            // Si esta línea pertenece al mismo pasajero y ruta:
//            if (lineCombo.equals(targetCombo)) {
//                //indice 9 es la posicion donde deberia estar el loc
//                parts.set(9, loc);
//                line = String.join(",", parts);
//            }
//            updatedLines.add(line);
//        }
//
//        Files.write(file, updatedLines);
//    }

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



