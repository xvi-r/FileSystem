package com.example.demo.controller;

import com.example.demo.service.CsvService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

}
