package com.example.demo.config;

import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        //metemos el admin por defecto si no esta creado todavia
        if (userRepository.findByRole(Role.ROLE_ADMIN).isEmpty()) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin123"))
                    .email("admin@airtortilla.com")
                    .role(Role.ROLE_ADMIN)
                    .build();
            userRepository.save(admin);
            // metemos log para saber que se creo al arrancar la app
            log.info("created default admin account: username='admin'");
        }
    }
}
