package com.example.demo.mapper;

import com.example.demo.model.Person;
import com.example.demo.model.Reserva;

final public class ReservasMapper {

    public static Reserva stringArrayToReserva(String[] array) {
        Person person = new Person(array[3], array[4], array[5], array[6], array[7]);
        return new Reserva(array[0],array[1],array[2], person);
    }
}
