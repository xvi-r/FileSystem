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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;

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
               reservaList.add( ReservasMapper.stringArrayToReserva(line.split(",")));
            }
        } catch (Exception e ) {
            throw new RuntimeException(e);
        }
    }

    public void storeCsv(MultipartFile file) throws IOException {
        Path uploadDirectory = Paths.get("uploads/baseCsv");

        Path filePath = uploadDirectory.resolve(file.getOriginalFilename());

        Files.copy(
                file.getInputStream(),
                filePath,
                StandardCopyOption.REPLACE_EXISTING
        );
    }
}
