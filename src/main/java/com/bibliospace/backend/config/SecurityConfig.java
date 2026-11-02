package com.bibliospace.backend.config;

import com.bibliospace.backend.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. Habilitamos CORS y desactivamos CSRF con la sintaxis lambda recomendada
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 2. Login, registro y recuperación de contraseña son públicos
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register",
                        "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Para que los errores lleguen con su código real (400, 404...) y no como 403
                .requestMatchers("/error").permitAll()
                // Documentación de la API (se puede apagar con API_DOCS=false)
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Todas las demás rutas requieren token (los permisos por rol están en cada controlador)
                .anyRequest().authenticated()
            )
            // Sin token o con token inválido respondemos 401, así el frontend sabe que debe volver al login
            .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write("{\"message\":\"Tu sesión expiró. Vuelve a iniciar sesión.\"}");
            }))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
