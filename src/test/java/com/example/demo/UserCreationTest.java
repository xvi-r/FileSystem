package com.example.demo;

import com.example.demo.dto.CreateUserRequest;
import com.example.demo.dto.UserResponseDto;
import com.example.demo.model.Role;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UserCreationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    //limpiamos usuarios que no sean el admin para que no fallen las pruebas por username repetido
    @BeforeEach
    void setUp() {
        userRepository.findAll().forEach(u -> {
            if (!"admin".equals(u.getUsername())) {
                userRepository.delete(u);
            }
        });
    }

    // test simple para comprobar que el servicio crea bien los usuarios con su rol y activados
    @Test
    void testCrearUsuarioEmpleado() {
        //preparamos la peticion con los datos basicos del nuevo usuario
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername("empleado_test");
        req.setPassword("clave123");
        req.setEmail("empleado_test@airtortilla.com");
        req.setRole(Role.ROLE_EMPLOYEE);

        // creamos el usuario llamando al servicio
        UserResponseDto res = userService.createUser(req);

        //comprobamos que se ha creado con un id valido
        assertNotNull(res.getId());
        assertEquals("empleado_test", res.getUsername());
        assertEquals(Role.ROLE_EMPLOYEE, res.getRole());
        assertTrue(res.isEnabled());

        // y comprobamos que de verdad esta guardado en el repositorio
        assertTrue(userRepository.existsByUsername("empleado_test"));
    }
}
