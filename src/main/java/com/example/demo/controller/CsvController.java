package com.example.demo.controller;

import com.example.demo.service.CsvService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@AllArgsConstructor
@RestController
@RequestMapping("/files")
public class CsvController {
    private CsvService csvService;

    @GetMapping("/reservas")
    public String getAllReservas() {
        return csvService.test();
    }

    @GetMapping("/generate-reservas")
    public ResponseEntity<String> generateReservas() {
        csvService.generateReservasFromCsv();
        return ResponseEntity.ok("Reservas Generado");
    }

    @PostMapping("/upload-csv")
    //RequestParam("file") para poner el file en el argumento
    public ResponseEntity<String> uploadCsv(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok("Reserva Uploaded Succesfully");
    }

}
