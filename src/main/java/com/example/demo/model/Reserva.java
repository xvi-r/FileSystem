package com.example.demo.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "reservas",
    indexes = {
        @Index(name = "idx_reserva_identifier", columnList = "identifier", unique = true),
        @Index(name = "idx_reserva_destino", columnList = "destino"),
        @Index(name = "idx_reserva_localizador", columnList = "localizador")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "identifier", length = 20, unique = true)
    private String identifier;

    @Builder.Default
    @Column(name = "localizador", length = 20)
    private String localizador = "";

    @Column(name = "destino", length = 10)
    private String destino;

    @Column(name = "origin", length = 10)
    private String origin;

    @Column(name = "flight_number", length = 30)
    private String flightNumber;

    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.EAGER)
    @JoinColumn(name = "passenger_id", nullable = false)
    private Person passenger;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (localizador == null) {
            localizador = "";
        }
    }

    public void mostrarInfo() {
        System.out.println("  Origen: " + origin);
        System.out.println("  Destino: " + destino);
        System.out.println("  Vuelo: " + flightNumber);
        System.out.println("  Pasajero: " + passenger);
    }

    public String getCsv() {
        return String.format(
                "%s,%s,%s,%s,%s,%s,%d,%s,%s,%s\n",
                identifier != null ? identifier : "",
                destino != null ? destino : "",
                origin != null ? origin : "",
                flightNumber != null ? flightNumber : "",
                passenger != null ? passenger.getNombre() : "",
                passenger != null ? passenger.getEmail() : "",
                passenger != null ? passenger.getEdad() : 0,
                passenger != null ? passenger.getDocumento() : "",
                passenger != null ? passenger.getAsiento() : "",
                localizador != null ? localizador.trim() : ""
        );
    }
}
