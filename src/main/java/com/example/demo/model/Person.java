package com.example.demo.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "persons",
    indexes = {
        @Index(name = "idx_person_documento", columnList = "documento", unique = true),
        @Index(name = "idx_person_nombre", columnList = "nombre")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", length = 120, nullable = false)
    private String nombre;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "edad")
    private int edad;

    @Column(name = "documento", length = 50, unique = true, nullable = false)
    private String documento;

    @Column(name = "asiento", length = 10)
    private String asiento;

    public String getCsvString() {
        return String.format("%s,%s,%d,%s,%s", nombre, email, edad, documento, asiento);
    }
}
