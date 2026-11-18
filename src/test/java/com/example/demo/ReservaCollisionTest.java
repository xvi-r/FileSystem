package com.example.demo;

import com.example.demo.model.Person;
import com.example.demo.model.Reserva;
import com.example.demo.repository.PersonRepository;
import com.example.demo.repository.ReservaRepository;
import com.example.demo.service.ReservaDatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ReservaCollisionTest {

    @Autowired
    private ReservaDatabaseService reservaDatabaseService;

    @Autowired
    private ReservaRepository reservaRepository;

    @Autowired
    private PersonRepository personRepository;

    //limpiamos las tablas antes del test pa que no queden datos raros
    @BeforeEach
    void setUp() {
        reservaRepository.deleteAll();
        personRepository.deleteAll();
        reservaDatabaseService.resetCounters();
    }

    // test para comprobar que la colision se detecta si coinciden nombre origen y destino
    @Test
    void testColisionDosPasajerosMismaRuta() {
        // creamos el primer pasajero
        Person p1 = Person.builder()
                .nombre("Daniel")
                .documento("00000001R")
                .email("daniel1@test.com")
                .edad(34)
                .asiento("18B")
                .build();

        Reserva r1 = Reserva.builder()
                .passenger(p1)
                .origin("FR")
                .destino("ES")
                .flightNumber("KE3181")
                .build();

        //guardamos la primera reserva en la base de datos
        Reserva guardada1 = reservaDatabaseService.saveOrUpdate(r1);

        assertNotNull(guardada1.getIdentifier());
        //como es el unico de momento no tiene colision asi que el localizador va vacio
        assertEquals("", guardada1.getLocalizador());

        //ahora creamos un segundo pasajero con el mismo nombre y la misma ruta pero otro dni
        Person p2 = Person.builder()
                .nombre("Daniel")
                .documento("00000002S")
                .email("daniel2@test.com")
                .edad(29)
                .asiento("19C")
                .build();

        Reserva r2 = Reserva.builder()
                .passenger(p2)
                .origin("FR")
                .destino("ES")
                .flightNumber("IB1234")
                .build();

        Reserva guardada2 = reservaDatabaseService.saveOrUpdate(r2);

        // aqui ya colisionan entonces tiene que tener un LOC asignado
        assertNotNull(guardada2.getLocalizador());
        assertTrue(guardada2.getLocalizador().startsWith("LOC-"));

        //y la primera reserva tiene que haberse actualizado con el mismo localizador
        Reserva r1Actualizada = reservaRepository.findById(guardada1.getId()).orElseThrow();
        assertEquals(guardada2.getLocalizador(), r1Actualizada.getLocalizador());
    }
}
