package com.example.demo.controller;

import com.example.demo.service.CsvService;
import com.example.demo.service.ZipService;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@AllArgsConstructor
@RestController
@RequestMapping("/files")

public class CsvController {
    private CsvService csvService;
    private ZipService zipService;

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

    @GetMapping("/process-csvç")
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
}
