package com.espacia.backend.demo;

import com.espacia.backend.entities.Incident;
import com.espacia.backend.entities.IncidentStatus;
import com.espacia.backend.entities.Notification;
import com.espacia.backend.entities.Reservation;
import com.espacia.backend.entities.ReservationStatus;
import com.espacia.backend.entities.Role;
import com.espacia.backend.entities.Space;
import com.espacia.backend.entities.User;
import com.espacia.backend.repositories.IncidentRepository;
import com.espacia.backend.repositories.NotificationRepository;
import com.espacia.backend.repositories.ReservationRepository;
import com.espacia.backend.repositories.SpaceRepository;
import com.espacia.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

// Cuentas y datos de demostración: usuarios de ejemplo con reservas, incidencias y notificaciones
// para que los paneles no se vean vacíos. Con el modo demostración (DEMO_MODE=true) también
// existe un administrador de solo consulta y todo se reinicia cada noche (ver DemoResetJob).
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoDataService {

    public static final String DOMINIO = "@espacia.local";

    // Cuentas de demostración: rol, nombre y correo
    public static final List<DemoAccount> CUENTAS = List.of(
            new DemoAccount(Role.ROLE_ESTUDIANTE, "Camila Torres", "estudiante" + DOMINIO),
            new DemoAccount(Role.ROLE_ESTUDIANTE, "Julián Gómez", "julian.gomez" + DOMINIO),
            new DemoAccount(Role.ROLE_FUNCIONARIO, "Andrés Restrepo", "funcionario" + DOMINIO));

    public static final DemoAccount ADMIN_DEMO =
            new DemoAccount(Role.ROLE_ADMIN, "Administrador de demostración", "admin.demo" + DOMINIO);

    private final UserRepository userRepository;
    private final SpaceRepository spaceRepository;
    private final ReservationRepository reservationRepository;
    private final IncidentRepository incidentRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.demo-password:Demo12345}")
    private String demoPassword;

    @Value("${app.demo.enabled:false}")
    private boolean modoDemo;

    // Crea las cuentas que falten y, si todavía no tienen actividad, sus reservas de ejemplo
    @Transactional
    public void preparar() {
        boolean nuevas = !userRepository.existsByEmailIgnoreCase(CUENTAS.getFirst().email());
        asegurarCuentas();
        if (nuevas) {
            crearActividad();
            log.info("Datos de demostración listos. Cuentas: {} (contraseña de demostración)", cuentasVisibles().stream()
                    .map(DemoAccount::email).toList());
        }
    }

    // Borra lo que hicieron las cuentas de demostración y vuelve a dejar sus datos de ejemplo
    @Transactional
    public void reiniciar() {
        long reservas = reservationRepository.deleteByUsuarioDemoTrue();
        long incidencias = incidentRepository.deleteByReportadoPorDemoTrue();
        long notificaciones = notificationRepository.deleteByUsuarioDemoTrue();
        asegurarCuentas();
        crearActividad();
        log.info("Demostración reiniciada: se borraron {} reservas, {} incidencias y {} notificaciones",
                reservas, incidencias, notificaciones);
    }

    // Las cuentas que se pueden mostrar en la pantalla de inicio
    public List<DemoAccount> cuentasVisibles() {
        return modoDemo
                ? List.of(CUENTAS.get(0), CUENTAS.get(2), ADMIN_DEMO)
                : List.of(CUENTAS.get(0), CUENTAS.get(2));
    }

    public String password() {
        return demoPassword;
    }

    // Crea o corrige las cuentas: activas, con su rol y la contraseña de demostración
    private void asegurarCuentas() {
        List<DemoAccount> cuentas = modoDemo
                ? List.of(CUENTAS.get(0), CUENTAS.get(1), CUENTAS.get(2), ADMIN_DEMO)
                : CUENTAS;
        for (DemoAccount cuenta : cuentas) {
            User user = userRepository.findByEmailIgnoreCase(cuenta.email()).orElseGet(() -> User.builder()
                    .email(cuenta.email())
                    .build());
            user.setNombre(cuenta.nombre());
            user.setRol(cuenta.rol());
            user.setActivo(true);
            user.setDemo(true);
            if (user.getPassword() == null || !passwordEncoder.matches(demoPassword, user.getPassword())) {
                user.cambiarPassword(passwordEncoder.encode(demoPassword));
            }
            userRepository.save(user);
        }
    }

    private void crearActividad() {
        List<Space> espacios = spaceRepository.findAll();
        if (espacios.isEmpty()) {
            return;
        }
        User camila = userRepository.findByEmailIgnoreCase(CUENTAS.get(0).email()).orElseThrow();
        User julian = userRepository.findByEmailIgnoreCase(CUENTAS.get(1).email()).orElseThrow();

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
                .mensaje("Tu reserva en Sala de Estudio A1 finalizó. ¡Gracias por usar Espacia!").build());

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

    public record DemoAccount(Role rol, String nombre, String email) {
    }
}
