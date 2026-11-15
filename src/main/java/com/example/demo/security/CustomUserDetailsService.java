package com.example.demo.security;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
//esto es nuestra implementacion de UserDetailsService, cuando llega a autenticar spring necesita una manera de
//sacar el usuario adequado por un username aqui lo definimos es literalmente solo una llamada a la bdd, y construcion
//de una instancia de User (la de spring boot NOOO la nuestra)
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        //buscamos al usuario en nuestra tabla
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("user not found with username: " + username));

        //aqui es donde pasa el check si la cuenta esta activada o no
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.isEnabled(),
                true,
                true,
                true,
                // spring simplemente espera una lista de Authorities aqui, en mi caso solo tengo uno asi que creo una
                //lista con solo un elemento (singleton)
                Collections.singletonList(new SimpleGrantedAuthority(user.getRole().name()))
        );
    }
}
