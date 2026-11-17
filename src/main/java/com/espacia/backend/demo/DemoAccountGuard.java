package com.espacia.backend.demo;

import com.espacia.backend.entities.Role;
import com.espacia.backend.entities.User;
import com.espacia.backend.repositories.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

// Recorta lo que pueden hacer las cuentas de demostración, para que cualquiera pueda probar
// la aplicación sin dañarla:
//  - el administrador de demostración solo puede consultar (GET);
//  - ninguna cuenta de demostración puede cambiar su contraseña.
// Va dentro de la cadena de Spring Security, después del filtro que lee el token.
@RequiredArgsConstructor
public class DemoAccountGuard extends OncePerRequestFilter {

    static final String SOLO_CONSULTA = "En la demostración el administrador solo puede consultar. "
            + "Para probar reservas e incidencias entra como estudiante o funcionario.";
    static final String SIN_CAMBIO_CLAVE = "Las cuentas de demostración no pueden cambiar la contraseña.";

    private static final Set<String> LECTURA = Set.of("GET", "HEAD", "OPTIONS");

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || LECTURA.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        User user = userRepository.findByEmailIgnoreCase(auth.getName()).orElse(null);
        if (user != null && Boolean.TRUE.equals(user.getDemo())) {
            if (user.getRol() == Role.ROLE_ADMIN) {
                rechazar(response, SOLO_CONSULTA);
                return;
            }
            if (request.getRequestURI().startsWith("/api/v1/auth/password")) {
                rechazar(response, SIN_CAMBIO_CLAVE);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static void rechazar(HttpServletResponse response, String mensaje) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"" + mensaje + "\",\"code\":\"DEMO\"}");
    }
}
