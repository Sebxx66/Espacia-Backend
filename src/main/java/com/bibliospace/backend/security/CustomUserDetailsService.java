package com.bibliospace.backend.security;

import com.bibliospace.backend.entities.User;
import com.bibliospace.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + email));
        return toDetails(user);
    }

    // Una cuenta suspendida por el administrador no puede iniciar sesión
    public static UserDetails toDetails(User user) {
        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities(user.getRol().name())
                .disabled(Boolean.FALSE.equals(user.getActivo()))
                .build();
    }
}
