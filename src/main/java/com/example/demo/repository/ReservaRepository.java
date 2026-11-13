package com.example.demo.repository;

import com.example.demo.model.Reserva;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReservaRepository extends JpaRepository<Reserva, Long> {

    Optional<Reserva> findByIdentifier(String identifier);

    List<Reserva> findByDestino(String destino);

    List<Reserva> findByDestinoOrderByIdAsc(String destino);

    Page<Reserva> findByDestino(String destino, Pageable pageable);

    long countByDestino(String destino);

    List<Reserva> findByLocalizador(String localizador);

    // buscamos si hay colision comparando nombre origen y destino

    //esto nos da todas las collisiones
    @Query("SELECT r FROM Reserva r WHERE r.passenger.nombre = :nombre AND r.origin = :origin AND r.destino = :destino")
    List<Reserva> findCollisions(
            @Param("nombre") String nombre,
            @Param("origin") String origin,
            @Param("destino") String destino
    );

    @Query("SELECT COUNT(r) > 0 FROM Reserva r WHERE r.passenger.nombre = :nombre AND r.origin = :origin AND r.destino = :destino")
    boolean existsCollision(
            @Param("nombre") String nombre,
            @Param("origin") String origin,
            @Param("destino") String destino
    );

    // pillamos el ultimo id para saber por donde iba el contador si se reinicia la app
    Optional<Reserva> findTopByOrderByIdDesc();

    // saca los destinos distintos que hay en la base de datos
    @Query("SELECT DISTINCT r.destino FROM Reserva r WHERE r.destino IS NOT NULL AND r.destino != '' ORDER BY r.destino ASC")
    List<String> findDistinctDestinos();
}
