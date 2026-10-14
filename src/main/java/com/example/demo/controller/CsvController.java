package com.example.demo.controller;

import com.example.demo.service.CsvService;
import com.example.demo.service.LocalizerService;
import com.example.demo.service.ZipService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@AllArgsConstructor
@RestController
@RequestMapping("/files")

public class CsvController {
    //spring nos da constructor injection que automaticamente inyecta los beans necesarios para la creacion de una instancia de
    //esta clase
    private CsvService csvService;
    private ZipService zipService;
    private LocalizerService localizerService;

    @GetMapping("/reservas")
    public String getAllReservas() {
        return csvService.test();
    }

    @GetMapping("/generate-reservas")
    public ResponseEntity<String> generateReservas() {
        csvService.generateReservasFromCsv();
        return ResponseEntity.ok("Reservas Generado");
    }

    //accepta MULTIPART_FORM_DATA_VALUE para los ficheros
    @PostMapping(value = "/upload-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    //RequestParam("file") para poner el file en el argumento
    public ResponseEntity<String> uploadCsv(@RequestParam("file") MultipartFile file) throws IOException {
        csvService.storeCsv(file);
        csvService.processCsv();
        return ResponseEntity.ok("Reserva Uploaded Succesfully");
    }

    @GetMapping("/process-csv")
    public ResponseEntity<String> processCsv() throws IOException {
        csvService.processCsv();
        return ResponseEntity.ok("Csv successfully processed");
    }

    @GetMapping("/zip")
    public ResponseEntity<byte[]> getZip() throws IOException {
        return ResponseEntity.ok()
                //Sin decir que tipo de media es va a genera los bytes para un zip valido pero devuelve un fichero en si, sin extension de .zip
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"AirTortilla.zip\"")
                .body(zipService.createZip());
    }

    @PostMapping(
            value = "/line",
            consumes = "text/csv"
    )
    //@RequestBody coge el body entero y lo pasa como parametro, el body en si solo deberia ser una linea de csv
    public ResponseEntity<Void> uploadLine(@RequestBody String csv) throws IOException {

        csvService.processLine(csv);

        return ResponseEntity.ok().build();
    }

    //genera la mapa Csv a Lista de reservas para consultar
    @PostMapping("/consult/line/initialize")
    public ResponseEntity<String> initializeConsultation() throws IOException {
        csvService.initLineByLineReading();
        return ResponseEntity.ok("CSV files loaded successfully");
    }

    @GetMapping(value = "/consult/line", produces = "text/csv")
    public ResponseEntity<String> consultLine(
            @RequestParam(defaultValue = "all") String file,
            @RequestParam(defaultValue = "0") int index) throws IOException {

        String csvLine;
        try {
            csvLine = csvService.consultLine(file,index);
        } catch (ArrayIndexOutOfBoundsException e) {
            return ResponseEntity.noContent().build();
        }

        // al pusar siguente pero ya no quedan reservas
//        if (csvLine == null) {
//            return ResponseEntity.noContent().build();
//        }

        return ResponseEntity.ok(csvLine);
    }

    @GetMapping("/consult/files")
    public Map<String, Integer> getAvailableFiles() throws IOException {
        csvService.initLineByLineReading();
        return csvService.getAllCsvNames();
    }

//    @GetMapping("/test/local")
//    public Set<String> getSet() throws IOException {
//        localizerService.loadSeenCombinations();
//        return localizerService.seenCombinations;
//    }
}
