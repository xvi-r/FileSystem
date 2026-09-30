import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


public class Main {
    public static void main(String[] args) throws Exception {
        ArrayList<Reserva> reservas = new ArrayList<>();
        Path folder = Path.of("Destinos");
        Files.createDirectories(folder);

        try (BufferedReader reader = Files.newBufferedReader(Path.of("reservasAir.csv"))) {
            String line = reader.readLine();

            while ((line = reader.readLine()) != null) {
                reservas.add(ReservasMapper.stringArrayToReserva(line.split(",")));
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        for(Reserva reserva: reservas) {
            System.out.println("Reserva: " + (reservas.indexOf(reserva) + 1));
            reserva.mostrarInfo();
        }


        for (Reserva reserva : reservas) {
            List<Path> fold = Files.list(folder).toList();

            Path destinoFolder = Path.of("Destinos", reserva.getDestino());

            Path file = Path.of("Destinos", reserva.getDestino(), reserva.getFlightNumber() + ".txt" );
            Files.writeString(file, reserva.toFileString());
            //Destinos\AHT
            if(fold.contains(destinoFolder)) {
                System.out.println(reserva.getDestino() + " folder exists");
            } else {

                Files.createDirectories(destinoFolder);
                System.out.println("Created");
            }

            try {
                Files.createFile(file);
            } catch (FileAlreadyExistsException e) {
                System.out.println("Skipping");
            }
        }

    }
}
