package com.example.demo.service;

import com.example.demo.model.Reserva;
import com.example.demo.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

// servicio para generar el zip con los csv particionados al vuelo sacando los datos de la bd
@Service
@RequiredArgsConstructor
public class ZipService {

    private final ReservaRepository reservaRepository;

    public byte[] createZip() throws IOException {
        List<String> destinos = reservaRepository.findDistinctDestinos();
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd"));

        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output)) {

            if (destinos.isEmpty()) {
                ZipEntry emptyEntry = new ZipEntry("AirTortilla_VACIO.txt");
                zip.putNextEntry(emptyEntry);
                zip.write("No hay reservas en la base de datos actualmente.\n".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            } else {
                for (String destino : destinos) {
                    String fileName = "AirTortilla_%s_%s.csv".formatted(destino, dateStr);
                    ZipEntry entry = new ZipEntry(fileName);
                    zip.putNextEntry(entry);

                    // escribimos la cabecera tipica del csv
                    String header = "identifier,destino,origin,flightNumber,nombre,email,edad,documento,asiento,localizador\n";
                    zip.write(header.getBytes(StandardCharsets.UTF_8));

                    //volcamos las reservas de este destino que vienen de la bd
                    List<Reserva> reservas = reservaRepository.findByDestinoOrderByIdAsc(destino);
                    for (Reserva r : reservas) {
                        zip.write(r.getCsv().getBytes(StandardCharsets.UTF_8));
                    }

                    zip.closeEntry();
                }
            }

            zip.finish();
            return output.toByteArray();
        }
    }
}
