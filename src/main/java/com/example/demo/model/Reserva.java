package com.example.demo.model;

public class Reserva {
    final private String destino;
    final private String origin;
    private String flightNumber;
    private Person passenger;

    public Reserva(String destino, String origin, String flightNumber, Person passenger) {
        this.destino = destino;
        this.origin = origin;
        this.flightNumber = flightNumber;
        this.passenger = passenger;
    }

    @Override
    public String toString() {
        return "Reserva{" +
                "destino='" + destino + '\'' +
                ", origin='" + origin + '\'' +
                ", flightNumber='" + flightNumber + '\'' +
                ", passenger=" + passenger.toString() +
                '}';
    }

    public void mostrarInfo() {
        System.out.println("  Origen: " + origin);
        System.out.println("  Destino: " + destino);
        System.out.println("  Vuelo: " + flightNumber);
        System.out.println("  Pasajero: " + passenger);
    }
    public String getDestino() {
        return destino;
    }

    public String getOrigin() {
        return origin;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public void setFlightNumber(String flightNumber) {
        this.flightNumber = flightNumber;
    }

    public Person getPassenger() {
        return passenger;
    }

    public void setPassenger(Person passenger) {
        this.passenger = passenger;
    }

    public String toFileString() {
        return String.format("""
            ==============================
            RESERVA DE VUELO
            ==============================
            Destination : %s
            Origin      : %s
            Flight      : %s

            PASAJERO
            ------------------------------
            Name        : %s
            Age         : %d
            Document    : %s
            Seat        : %s
            Email       : %s
            ==============================
            """,
                destino,
                origin,
                flightNumber,
                passenger.getNombre(),
                passenger.getEdad(),
                passenger.getDocumento(),
                passenger.getAsiento(),
                passenger.getEmail()
        );
    }


}
