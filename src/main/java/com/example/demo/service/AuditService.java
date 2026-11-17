package com.example.demo.service;

import com.example.demo.model.AuditLog;
import com.example.demo.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    //guarda una accion en la tabla de auditoria pillando el usuario logeado si hay
    @Transactional
    public AuditLog logAction(String action, String entityType, String entityId, String details) {
        String username = getCurrentUsername();
        return logAction(username, action, entityType, entityId, details, null);
    }

    // sobrecarga por si queremos pasarle el usuario o la ip a mano
    @Transactional
    public AuditLog logAction(String username, String action, String entityType, String entityId, String details, String ipAddress) {
        AuditLog auditLog = AuditLog.builder()
                .username(username != null ? username : "SYSTEM")
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .details(details)
                .ipAddress(ipAddress)
                .timestamp(LocalDateTime.now())
                .build();

        AuditLog saved = auditLogRepository.save(auditLog);
        log.info("AUDIT: user='{}' action='{}' entity='{}:{}' details='{}'",
                saved.getUsername(), saved.getAction(), saved.getEntityType(), saved.getEntityId(), saved.getDetails());
        return saved;
    }

    //todos los logs devueltos paginados
    public Page<AuditLog> getAllLogs(int page, int size) {
        return auditLogRepository.findAll(
                PageRequest.of(page, size, Sort.by("timestamp").descending())
        );
    }

    // solo por usuario
    public List<AuditLog> getLogsByUser(String username) {
        return auditLogRepository.findByUsernameOrderByTimestampDesc(username);
    }

    //por tipo de accion
    public List<AuditLog> getLogsByAction(String action) {
        return auditLogRepository.findByActionOrderByTimestampDesc(action);
    }

    //en el caso que queries llamar el overload de logAction, esto de coge el username directamente del security context actual
    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        //getAuthentication(); devuelve anonymousUser el caso que el request no tiene un contexto/sesion vinculado
        // SYSTEM en el caso que ni hay un request asociado
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "SYSTEM";
    }
}
