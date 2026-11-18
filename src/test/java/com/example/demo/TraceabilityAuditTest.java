package com.example.demo;

import com.example.demo.model.AuditLog;
import com.example.demo.repository.AuditLogRepository;
import com.example.demo.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TraceabilityAuditTest {

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    //vaciamos los logs antes de empezar
    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
    }

    // test simple para comprobar que el servicio de auditoria guarda bien el log en la tabla
    @Test
    void testGuardarLogDeAuditoria() {
        // metemos un log a mano para probar
        auditService.logAction(
                "admin",
                "PRUEBA_TEST",
                "Reserva",
                "AT-0099",
                "cambio de prueba en el vuelo",
                "127.0.0.1"
        );

        //buscamos en la base de datos a ver si se guardo
        List<AuditLog> logs = auditLogRepository.findByActionOrderByTimestampDesc("PRUEBA_TEST");

        assertFalse(logs.isEmpty());
        assertEquals(1, logs.size());

        AuditLog logGuardado = logs.get(0);
        // comprobamos que los campos se han rellenado bien
        assertEquals("admin", logGuardado.getUsername());
        assertEquals("PRUEBA_TEST", logGuardado.getAction());
        assertEquals("Reserva", logGuardado.getEntityType());
        assertEquals("AT-0099", logGuardado.getEntityId());
        assertNotNull(logGuardado.getTimestamp());
    }
}
