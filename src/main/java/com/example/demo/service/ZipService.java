package com.example.demo.service;

import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
//servicio dedicado a generar el zip que vamos a mandar al frontend para la consulta batch
public class ZipService {

    public  byte[] createZip() throws IOException {
        List<Path> csvFiles;
        //encontramos todos los ficheros generados
        //isRegularFile para que evitamos los paths que aun no apuntan a un fichero
        try (Stream<Path> paths = Files.walk(Path.of("uploads", "reservas"))) {
            csvFiles = paths.filter(Files::isRegularFile).toList();

            //try with resources para cerrar los automaticamente
            try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output)) {

                for (Path csvFile : csvFiles) {
                    //generamos un nuevo fichero en el zip
                    ZipEntry entry = new ZipEntry(
                            csvFile.getFileName().toString()
                    );

                    //decimos al zip que estamos usando este fichero
                    zip.putNextEntry(entry);
                    //copiamos el contendio que corresponde
                    Files.copy(csvFile, zip);
                    zip.closeEntry();
                }

                zip.finish();
                return output.toByteArray();
            }
        }
    }
}
