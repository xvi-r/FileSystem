package com.example.demo.mapper;

import com.example.demo.model.Person;
import com.example.demo.model.Reserva;

final public class ReservasMapper {

    public static Reserva stringArrayToReserva(String[] array) {
        int edad = (array[4] != null && !array[4].isBlank()) ? Integer.parseInt(array[4].trim()) : 0;
        Person person = Person.builder()
                .nombre(array[3])
                .edad(edad)
                .documento(array[5])
                .asiento(array[6])
                .email(array[7])
                .build();

        return Reserva.builder()
                .destino(array[0])
                .origin(array[1])
                .flightNumber(array[2])
                .passenger(person)
                .localizador("")
                .build();
    }
}
