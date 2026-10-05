package com.example.demo.controller;

import com.example.demo.mapper.ReservasMapper;
import com.example.demo.service.FileService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AllArgsConstructor
@RestController
@RequestMapping("/files")
public class FileController {
    private FileService fileService;

    @GetMapping("/reservas")
    public String getAllReservas() {
        return fileService.test();
    }

    @GetMapping("/generate-reservas")
    public ResponseEntity<String> generateReservas() {
        fileService.generateReservasFromCsv();
        return ResponseEntity.ok("Reservas Generado");
    }

}
