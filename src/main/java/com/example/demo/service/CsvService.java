package com.example.demo.service;

import com.example.demo.mapper.ReservasMapper;
import com.example.demo.model.Reserva;
import org.springframework.stereotype.Service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.util.ArrayList;

@Service
public class CsvService {
    ArrayList<Reserva> reservaList = new ArrayList<>();

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
}
