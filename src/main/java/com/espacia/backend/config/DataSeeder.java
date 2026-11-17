package com.espacia.backend.config;

import com.espacia.backend.entities.Role;
import com.espacia.backend.entities.Space;
import com.espacia.backend.entities.User;
import com.espacia.backend.repositories.SpaceRepository;
import com.espacia.backend.repositories.UserRepository;
import com.espacia.backend.demo.DemoDataService;
import com.espacia.backend.services.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

// Datos mínimos para poder usar la app desde el primer arranque
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final SpaceRepository spaceRepository;
    private final DemoDataService demoDataService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.nombre}")
    private String adminNombre;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.seed.demo-data}")
    private boolean seedDemoData;

    // Modo demostración público (DEMO_MODE): incluye los datos de ejemplo aunque SEED_DEMO_DATA=false
    @Value("${app.demo.enabled:false}")
    private boolean modoDemo;

    @Override
    public void run(String... args) {
        crearAdministrador();
        if ((seedDemoData || modoDemo) && spaceRepository.count() == 0) {
            crearEspaciosDeEjemplo();
        }
        if (seedDemoData || modoDemo) {
            demoDataService.preparar();
        }
    }

    // Sin un administrador nadie podría crear funcionarios ni otros administradores
    private void crearAdministrador() {
        if (userRepository.existsByRol(Role.ROLE_ADMIN)) {
            return;
        }
        String email = AuthService.normalizeEmail(adminEmail);
        User admin = userRepository.findByEmailIgnoreCase(email).orElseGet(() -> User.builder()
                .nombre(adminNombre)
                .email(email)
                .password(passwordEncoder.encode(adminPassword))
                .build());
        admin.setRol(Role.ROLE_ADMIN);
        admin.setActivo(true);
        userRepository.save(admin);
        log.info("Administrador inicial listo: {} (cambia la contraseña después del primer ingreso)", email);
    }

    private void crearEspaciosDeEjemplo() {
        spaceRepository.saveAll(List.of(
                espacio("Sala de Estudio A1", "Sala versátil para trabajo grupal, presentaciones y reuniones de equipo.",
                        "SALA_ESTUDIO", 8, "Planta 2", "Proyector, Pizarra magnética, WiFi"),
                espacio("Sala de Lectura E1", "Espacio silencioso con luz natural para estudio individual.",
                        "SALA_ESTUDIO", 2, "Planta 1", "Luz natural, Enchufes USB-C, WiFi"),
                espacio("Sala de Conferencias C3", "Sala para clases, conferencias y videollamadas de grupo.",
                        "SALA_ESTUDIO", 16, "Planta 3", "TV 75\", Videoconferencia, Micrófono ambiental"),
                espacio("Laptop HP EliteBook", "Préstamo de laptop para trabajo académico, hasta 4 horas.",
                        "EQUIPO_TECNOLOGICO", 1, "Mostrador central", "Intel Core i7, 16 GB RAM, Windows 11"),
                espacio("MacBook Pro M2", "Ideal para diseño, edición de video y programación.",
                        "EQUIPO_TECNOLOGICO", 1, "Mostrador central", "Apple M2, 16 GB RAM, Final Cut Pro"),
                espacio("Taller de Fabricación D1", "Taller maker con herramientas de prototipado rápido.",
                        "TALLER", 12, "Semisótano", "Impresoras 3D, Cortadora láser, Arduino")
        ));
        log.info("Se crearon espacios de ejemplo");
    }

    private static Space espacio(String nombre, String descripcion, String tipo, int capacidad,
                                 String ubicacion, String equipamiento) {
        return Space.builder()
                .nombre(nombre)
                .descripcion(descripcion)
                .tipo(tipo)
                .capacidad(capacidad)
                .estado("ACTIVO")
                .ubicacion(ubicacion)
                .equipamiento(equipamiento)
                .build();
    }
}
