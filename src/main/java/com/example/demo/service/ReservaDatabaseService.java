package com.example.demo.service;

import com.example.demo.model.Person;
import com.example.demo.model.Reserva;
import com.example.demo.repository.ReservaRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservaDatabaseService {

    private final ReservaRepository reservaRepository;
    private final com.example.demo.repository.PersonRepository personRepository;
    private final AuditService auditService;

    //contadores atomicos pa generar codigos incrementales sin que se pisen hilos
    private final AtomicLong identifierCounter = new AtomicLong(0);
    private final AtomicLong localizerCounter = new AtomicLong(0);

    // post construct siempre se ejecuta despues de la creacion del bean
    @PostConstruct
    public void initCounters() {
        //miramos el ultimo id guardado en la bd para no pisar ids si reiniciamos
        identifierCounter.set(
                reservaRepository.findTopByOrderByIdDesc()
                        .map(Reserva::getId)
                        .orElse(0L)
        );

        localizerCounter.set(0);
        reservaRepository.findAll().forEach(r -> {
            String loc = r.getLocalizador();
            if (loc != null && loc.startsWith("LOC-")) {
                try {
                    long val = Long.parseLong(loc.substring(4).trim());
                    localizerCounter.accumulateAndGet(val, Math::max);
                } catch (NumberFormatException ignored) {}
            }
        });
        log.info("database counters initialized: identifier={}, localizer={}",
                identifierCounter.get(), localizerCounter.get());
    }

    public void resetCounters() {
        initCounters();
    }

    public synchronized String generateNextIdentifier() {
        return "AT-%04d".formatted(identifierCounter.incrementAndGet());
    }

    public synchronized String generateNextLocalizer() {
        return "LOC-%04d".formatted(localizerCounter.incrementAndGet());
    }

    //guarda o actualiza la reserva comprobando si colisiona con otra
    @Transactional
    public Reserva saveOrUpdate(Reserva reserva) {
        // un monton de ternary simplemente en el caso que la linea del csv
        // no tiene algun valor pero por si acaso
        String nombre = reserva.getPassenger() != null ? reserva.getPassenger().getNombre() : "";
        String origin = reserva.getOrigin() != null ? reserva.getOrigin() : "";
        String destino = reserva.getDestino() != null ? reserva.getDestino() : "";

        //si no tiene identificador le generamos el siguiente AT-XXXX
        if (reserva.getIdentifier() == null || reserva.getIdentifier().isBlank()) {
            reserva.setIdentifier(generateNextIdentifier());
        }

        // si el pasajero ya existe por dni lo reutilizamos para no duplicarlo ni que pete la clave unica
        if (reserva.getPassenger() != null) {
            Person p = reserva.getPassenger();
            if (p.getDocumento() != null && !p.getDocumento().isBlank()) {
                Person existing = personRepository.findByDocumento(p.getDocumento().trim()).orElse(null);
                if (existing != null) {
                    reserva.setPassenger(existing);
                } else {
                    reserva.setPassenger(personRepository.save(p));
                }
            }
        }

        //miramos en la bd si ya habia vuelos de este pasajero en la misma ruta
        List<Reserva> existingCollisions = reservaRepository.findCollisions(nombre, origin, destino);

        if (!existingCollisions.isEmpty()) {
            // si ya habia colision anterior pillamos ese localizador
            String sharedLoc = existingCollisions.stream()
                    .map(Reserva::getLocalizador)
                    .filter(loc -> loc != null && !loc.isBlank())
                    .findFirst()
                    .orElse(null);

            if (sharedLoc == null || sharedLoc.isBlank()) {
                //si es la primera colision generamos uno nuevo y parcheamos los anteriores
                sharedLoc = generateNextLocalizer();

                //en teoria no hace falta el loop porque la unica possible collision sin un loc ya existente seria la
                // primera collision
                for (Reserva prior : existingCollisions) {
                    if (prior.getLocalizador() == null || prior.getLocalizador().isBlank()) {
                        prior.setLocalizador(sharedLoc);
                        reservaRepository.save(prior);
                    }
                }

                // guardamos en auditoria que se resolvio una colision
                auditService.logAction(
                        "COLLISION_RESOLVED",
                        "Reserva",
                        reserva.getIdentifier(),
                        "collision resolved with locator '%s' for passenger '%s' on route %s->%s"
                                .formatted(sharedLoc, nombre, origin, destino)
                );
            }

            reserva.setLocalizador(sharedLoc);
        } else if (reserva.getLocalizador() == null) {
            reserva.setLocalizador("");
        }

        //guardamos la reserva en postgres
        Reserva saved = reservaRepository.save(reserva);

        // registramos en la auditoria que se creo la reserva
        auditService.logAction(
                "RESERVA_CREATED",
                "Reserva",
                saved.getIdentifier(),
                "created reservation: flight=%s route=%s->%s passenger=%s locator=%s"
                        .formatted(saved.getFlightNumber(), origin, destino, nombre, saved.getLocalizador())
        );

        return saved;
    }

    public List<Reserva> getAll() {
        return reservaRepository.findAll();
    }

    public Optional<Reserva> getByIdentifier(String identifier) {
        return reservaRepository.findByIdentifier(identifier);
    }

    public List<Reserva> getByDestino(String destino) {
        return reservaRepository.findByDestino(destino);
    }

    public List<Reserva> getByLocalizador(String localizador) {
        return reservaRepository.findByLocalizador(localizador);
    }

    public long count() {
        return reservaRepository.count();
    }
}
