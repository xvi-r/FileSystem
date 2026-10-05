package com.example.demo.service;

import org.springframework.stereotype.Service;

@Service
public class FileService {

    public String test() {
        return "HI from service" + this.getClass();
    }

    public void generateReservasFromCsv() {

    }
}
