package com.bibliospace.backend.config;

import com.bibliospace.backend.entities.Incident;
import com.bibliospace.backend.entities.IncidentStatus;
import com.bibliospace.backend.entities.Notification;
import com.bibliospace.backend.entities.Reservation;
import com.bibliospace.backend.entities.ReservationStatus;
import com.bibliospace.backend.entities.Role;
import com.bibliospace.backend.entities.Space;
import com.bibliospace.backend.entities.User;
import com.bibliospace.backend.repositories.IncidentRepository;
import com.bibliospace.backend.repositories.NotificationRepository;
import com.bibliospace.backend.repositories.ReservationRepository;
import com.bibliospace.backend.repositories.SpaceRepository;
import com.bibliospace.backend.repositories.UserRepository;
import com.bibliospace.backend.services.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

// Datos mínimos para poder usar la app desde el primer arranque
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final SpaceRepository spaceRepository;
    private final ReservationRepository reservationRepository;
    private final IncidentRepository incidentRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.nombre}")
    private String adminNombre;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.seed.demo-data}")
    private boolean seedDemoData;

    // Contraseña de las cuentas de demostración (solo existen si app.seed.demo-data=true)
    @Value("${app.seed.demo-password:Demo12345}")
    private String demoPassword;

    @Override
    public void run(String... args) {
        crearAdministrador();
        if (seedDemoData && spaceRepository.count() == 0) {
            crearEspaciosDeEjemplo();
        }
        if (seedDemoData) {
            crearDatosDeDemostracion();
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

    // Usuarios, reservas, incidencias y notificaciones de ejemplo para que los paneles
    // no se vean vacíos en una demostración. Se crea una sola vez (si ya existe la
    // cuenta de estudiante de demostración, no se vuelve a ejecutar).
    private void crearDatosDeDemostracion() {
        final String emailEstudiante = "estudiante@bibliospace.local";
        if (userRepository.existsByEmailIgnoreCase(emailEstudiante)) {
            return;
        }
        List<Space> espacios = spaceRepository.findAll();
        if (espacios.isEmpty()) {
            return;
        }

        User camila = usuarioDemo("Camila Torres", emailEstudiante, Role.ROLE_ESTUDIANTE);
        User julian = usuarioDemo("Julián Gómez", "julian.gomez@bibliospace.local", Role.ROLE_ESTUDIANTE);
        usuarioDemo("Andrés Restrepo", "funcionario@bibliospace.local", Role.ROLE_FUNCIONARIO);

        Space salaA1 = buscar(espacios, "Sala de Estudio A1");
        Space salaE1 = buscar(espacios, "Sala de Lectura E1");
        Space salaC3 = buscar(espacios, "Sala de Conferencias C3");
        Space laptop = buscar(espacios, "Laptop HP EliteBook");
        Space mac = buscar(espacios, "MacBook Pro M2");
        Space taller = buscar(espacios, "Taller de Fabricación D1");

        LocalDate hoy = LocalDate.now();

        // Historial de las últimas semanas (alimenta estadísticas y "mis reservas")
        reservaDemo(camila, salaA1, hoy.minusDays(1), 10, 12, ReservationStatus.FINALIZADA, "Repaso para el parcial de Cálculo");
        reservaDemo(julian, salaE1, hoy.minusDays(1), 8, 10, ReservationStatus.FINALIZADA, "Lectura del capítulo de Ingeniería de Software");
        reservaDemo(camila, taller, hoy.minusDays(2), 15, 17, ReservationStatus.FINALIZADA, "Prototipo impreso en 3D");
        reservaDemo(julian, laptop, hoy.minusDays(3), 14, 16, ReservationStatus.FINALIZADA, "Entrega de taller de programación");
        reservaDemo(camila, laptop, hoy.minusDays(4), 9, 11, ReservationStatus.FINALIZADA, "Trabajo de bases de datos");
        reservaDemo(julian, salaA1, hoy.minusDays(5), 14, 17, ReservationStatus.FINALIZADA, "Reunión del grupo de investigación");
        reservaDemo(camila, salaC3, hoy.minusDays(6), 9, 11, ReservationStatus.FINALIZADA, "Ensayo de sustentación");
        reservaDemo(julian, mac, hoy.minusDays(7), 10, 13, ReservationStatus.FINALIZADA, "Edición de video del proyecto final");
        reservaDemo(camila, salaA1, hoy.minusDays(8), 16, 18, ReservationStatus.FINALIZADA, "Estudio en grupo");
        reservaDemo(julian, taller, hoy.minusDays(10), 9, 12, ReservationStatus.FINALIZADA, "Pruebas con Arduino");
        reservaDemo(camila, salaE1, hoy.minusDays(12), 8, 10, ReservationStatus.FINALIZADA, "Escritura del ensayo");
        reservaDemo(julian, salaC3, hoy.minusDays(14), 14, 16, ReservationStatus.FINALIZADA, "Videollamada con el asesor de tesis");
        reservaDemo(camila, mac, hoy.minusDays(15), 9, 11, ReservationStatus.CANCELADA, "Diseño de póster");

        // Próximas reservas
        reservaDemo(camila, salaC3, hoy.plusDays(1), 9, 11, ReservationStatus.CONFIRMADA, "Sustentación del proyecto de aula");
        reservaDemo(julian, salaA1, hoy.plusDays(1), 14, 16, ReservationStatus.CONFIRMADA, "Reunión de equipo");
        reservaDemo(camila, taller, hoy.plusDays(3), 10, 12, ReservationStatus.CONFIRMADA, "Impresión de piezas");
        reservaDemo(julian, mac, hoy.plusDays(2), 9, 11, ReservationStatus.CANCELADA, "Edición de video");

        // Una reserva en curso (el espacio se ve "ocupado") y otra más tarde hoy, solo en horario de atención
        int hora = LocalTime.now().getHour();
        if (hora >= 8 && hora <= 18) {
            reservaDemo(julian, mac, hoy, hora, hora + 1, ReservationStatus.CONFIRMADA, "Edición de video");
        }
        if (hora >= 8 && hora <= 16) {
            reservaDemo(camila, salaE1, hoy, hora + 2, hora + 4, ReservationStatus.CONFIRMADA, "Estudio individual");
        }

        // Incidencias: una abierta y una ya resuelta
        if (mac != null) {
            incidentRepository.save(Incident.builder()
                    .espacio(mac).reportadoPor(camila)
                    .descripcion("El cargador del equipo está suelto y la batería dura muy poco.")
                    .estado(IncidentStatus.ABIERTA).build());
        }
        if (salaA1 != null) {
            incidentRepository.save(Incident.builder()
                    .espacio(salaA1).reportadoPor(julian)
                    .descripcion("El proyector no encendía al iniciar la reunión.")
                    .estado(IncidentStatus.RESUELTA).resueltaEn(LocalDateTime.now()).build());
        }

        // Notificaciones de la estudiante (se guardan directo: no envían correo)
        notificationRepository.save(Notification.builder().usuario(camila).tipo("CONFIRMACION").leida(false)
                .mensaje("Tu reserva en Sala de Conferencias C3 quedó confirmada.").build());
        notificationRepository.save(Notification.builder().usuario(camila).tipo("FINALIZADA").leida(true)
                .mensaje("Tu reserva en Sala de Estudio A1 finalizó. ¡Gracias por usar BiblioSpace!").build());

        log.info("Datos de demostración listos. Cuentas: estudiante@bibliospace.local, funcionario@bibliospace.local (contraseña de demostración)");
    }

    private User usuarioDemo(String nombre, String email, Role rol) {
        return userRepository.findByEmailIgnoreCase(email).orElseGet(() -> userRepository.save(User.builder()
                .nombre(nombre)
                .email(email)
                .password(passwordEncoder.encode(demoPassword))
                .rol(rol)
                .activo(true)
                .build()));
    }

    private static Space buscar(List<Space> espacios, String nombre) {
        return espacios.stream().filter(e -> nombre.equalsIgnoreCase(e.getNombre())).findFirst().orElse(null);
    }

    private void reservaDemo(User usuario, Space espacio, LocalDate fecha, int horaInicio, int horaFin,
                             ReservationStatus estado, String motivo) {
        if (espacio == null) {
            return;
        }
        reservationRepository.save(Reservation.builder()
                .usuario(usuario)
                .espacio(espacio)
                .fecha(fecha)
                .horaInicio(LocalTime.of(horaInicio, 0))
                .horaFin(LocalTime.of(horaFin, 0))
                .estado(estado)
                .motivo(motivo)
                .build());
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
