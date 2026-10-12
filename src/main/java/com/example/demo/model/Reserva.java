package com.example.demo.model;

public class Reserva {

    private String identifier;
    private String localizador = "";

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

    public Reserva(String identifier, String destino, String origin, String flightNumber, Person passenger) {
        this.identifier = identifier;
        this.destino = destino;
        this.origin = origin;
        this.flightNumber = flightNumber;
        this.passenger = passenger;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getLocalizador() {
        return localizador;
    }

    public void setLocalizador(String localizador) {
        this.localizador = localizador;
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


    public String getCsv() {
        return String.format(
                "%s,%s,%s,%s,%s,%s,%d,%s,%s,%s\n",
                identifier,
                destino,
                origin,
                flightNumber,
                passenger.getNombre(),
                passenger.getEmail(),
                passenger.getEdad(),
                passenger.getDocumento(),
                passenger.getAsiento(),
                "          "
        );
    }


}
