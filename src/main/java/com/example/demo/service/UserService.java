package com.example.demo.service;

import com.example.demo.dto.CreateUserRequest;
import com.example.demo.dto.UserResponseDto;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

//servicio para gestionar los usuarios y los roles de la app
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    //crear un nuevo usuario comprobando que no exista ya
    @Transactional
    public UserResponseDto createUser(CreateUserRequest request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("password is required");
        }
        // miramos si el username ya esta pillado en la bdd
        if (userRepository.existsByUsername(request.getUsername().trim())) {
            throw new IllegalArgumentException("username '" + request.getUsername() + "' is already taken");
        }
        //si pasa email comprobamos que tampoco este repetido
        if (request.getEmail() != null && !request.getEmail().isBlank() &&
                userRepository.existsByEmail(request.getEmail().trim())) {
            throw new IllegalArgumentException("email '" + request.getEmail() + "' is already registered");
        }

        // por defecto le ponemos empleado si no especifican rol
        Role role = request.getRole() != null ? request.getRole() : Role.ROLE_EMPLOYEE;
        String hashedPassword = passwordEncoder.encode(request.getPassword().trim());

        User user = User.builder()
                .username(request.getUsername().trim())
                .password(hashedPassword)
                .email(request.getEmail() != null ? request.getEmail().trim() : null)
                .role(role)
                .enabled(true)
                .build();

        User saved = userRepository.save(user);

        //guardamos en auditoria que se creo el usuario nuevo
        auditService.logAction(
                "USER_CREATED",
                "User",
                saved.getId().toString(),
                "created user '" + saved.getUsername() + "' with role " + saved.getRole()
        );

        log.info("admin created new user: {} with role: {}", saved.getUsername(), saved.getRole());
        return UserResponseDto.fromEntity(saved);
    }

    // saca todos los usuarios registrados
    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(UserResponseDto::fromEntity)
                .toList();
    }

    //busca un usuario por su username
    public Optional<UserResponseDto> getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(UserResponseDto::fromEntity);
    }

    // para que el admin pueda bloquear o desbloquear a alguien
    @Transactional
    public UserResponseDto toggleUserStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found with id: " + userId));

        //le damos la vuelta al booleano
        user.setEnabled(!user.isEnabled());
        User updated = userRepository.save(user);

        // dejamos rastro en la tabla de auditoria
        auditService.logAction(
                "USER_STATUS_TOGGLED",
                "User",
                updated.getId().toString(),
                "user status changed for '" + updated.getUsername() + "' to enabled=" + updated.isEnabled()
        );

        log.info("user status '{}' changed to enabled={}", updated.getUsername(), updated.isEnabled());
        return UserResponseDto.fromEntity(updated);
    }
}
