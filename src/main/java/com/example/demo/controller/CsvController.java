package com.example.demo.controller;

import com.example.demo.model.Reserva;
import com.example.demo.service.CsvService;
import com.example.demo.service.ReservaDatabaseService;
import com.example.demo.service.ZipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class CsvController {

    private final CsvService csvService;
    private final ReservaDatabaseService reservaDatabaseService;
    private final ZipService zipService;

    //devuelve todas las reservas en la base de datos
    @GetMapping("/db/reservas")
    public ResponseEntity<List<Reserva>> getAllDbReservas() {
        return ResponseEntity.ok(reservaDatabaseService.getAll());
    }

    // busca una reserva por su identificador unico
    @GetMapping("/db/reservas/{identifier}")
    public ResponseEntity<Reserva> getDbReservaByIdentifier(@PathVariable String identifier) {
        return reservaDatabaseService.getByIdentifier(identifier)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    //reservas filtradas por codigo de destino
    @GetMapping("/db/reservas/destino/{destino}")
    public ResponseEntity<List<Reserva>> getDbReservasByDestino(@PathVariable String destino) {
        return ResponseEntity.ok(reservaDatabaseService.getByDestino(destino));
    }

    @GetMapping("/test")
    public String test() {
        return csvService.test();
    }

    @GetMapping("/generate-reservas")
    public ResponseEntity<String> generateReservas() {
        csvService.generateReservasFromCsv();
        return ResponseEntity.ok("reservas generated successfully");
    }

    //subida de csv multipart para procesar y meter en la bdd
    @PostMapping(value = {"/upload", "/upload-csv"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadCsv(@RequestParam("file") MultipartFile file) throws IOException {
        int count = csvService.processUploadedCsv(file);
        return ResponseEntity.ok("imported " + count + " reservations into database successfully");
    }

    @PostMapping({"/process", "/process-csv"})
    public ResponseEntity<String> processCsv() throws IOException {
        csvService.processCsv();
        return ResponseEntity.ok("csv processed successfully");
    }

    // genera el zip con los csv particionados al vuelo y lo manda como attachment
    @GetMapping("/zip")
    public ResponseEntity<byte[]> getZip() throws IOException {
        byte[] zipBytes = zipService.createZip();
        log.info("generating zip download, size: {} bytes", zipBytes.length);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"AirTortilla.zip\"")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(zipBytes.length)
                .body(zipBytes);
    }

    @GetMapping({"/all", "/consult/files", "/files"})
    public Map<String, Integer> getAll() {
        return csvService.getAllCsvNames();
    }

    @PostMapping({"/upload-line", "/line"})
    public ResponseEntity<Void> uploadLine(@RequestBody String csv) throws IOException {
        csvService.processLine(csv);
        return ResponseEntity.ok().build();
    }

    @RequestMapping(value = {"/consult-files", "/consult/line/initialize", "/consult/initialize"}, method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> initializeConsultation() {
        csvService.initLineByLineReading();
        return ResponseEntity.ok("database consultation initialized");
    }

    @GetMapping({"/consult-file", "/consult/line"})
    public ResponseEntity<String> consultLine(
            @RequestParam(name = "file", required = false, defaultValue = "all") String csvFile,
            @RequestParam(name = "index", required = false, defaultValue = "0") int index
    ) {
        String csvLine = csvService.consultLine(csvFile, index);
        if (csvLine == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(csvLine);
    }
}
