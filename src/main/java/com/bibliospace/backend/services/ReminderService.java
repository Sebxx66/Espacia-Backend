package com.bibliospace.backend.services;

import com.bibliospace.backend.entities.Reservation;
import com.bibliospace.backend.entities.ReservationStatus;
import com.bibliospace.backend.repositories.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Cada minuto busca reservas confirmadas que empiezan pronto y envía un recordatorio por correo (una sola vez).
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ReservationRepository reservationRepository;
    private final EmailService emailService;
    private final BookingRulesService bookingRulesService;

    @Value("${app.reminder-minutes:60}")
    private int minutosAntes;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    @Transactional
    public void enviarRecordatorios() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime limite = ahora.plusMinutes(minutosAntes);
        List<Reservation> candidatas = reservationRepository.findByEstadoAndRecordatorioEnviadoFalseAndFechaBetween(
                ReservationStatus.CONFIRMADA, ahora.toLocalDate(), limite.toLocalDate());

        for (Reservation r : candidatas) {
            LocalDateTime inicio = r.inicio();
            if (inicio.isBefore(ahora) || inicio.isAfter(limite)) {
                continue;
            }
            // Ya se marca como atendida: no se vuelve a evaluar en el próximo minuto
            r.setRecordatorioEnviado(true);

            // Si se reservó con menos de una hora de anticipación, la confirmación ya sirve de aviso
            if (r.getCreadaEn() != null && r.getCreadaEn().isAfter(inicio.minusMinutes(minutosAntes))) {
                continue;
            }
            String email = r.getUsuario().getEmail();
            if (email == null || email.toLowerCase().endsWith("@bibliospace.local")) {
                continue; // cuentas de demostración: no tienen buzón real
            }
            String cuando = r.getFecha().equals(ahora.toLocalDate()) ? "hoy" : "mañana";
            int horas = bookingRulesService.get().getHorasCancelacion();
            String plazo = horas == 0 ? "antes de que empiece" : "hasta " + horas + (horas == 1 ? " hora" : " horas") + " antes";
            emailService.sendReminder(email, r.getUsuario().getNombre(),
                    "Tu reserva en " + r.getEspacio().getNombre() + " empieza " + cuando + " a las "
                            + r.getHoraInicio().format(HORA) + " y termina a las " + r.getHoraFin().format(HORA)
                            + ". Recuerda llegar a tiempo; si ya no la necesitas, cancélala " + plazo + " para liberar el espacio.");
            log.info("Recordatorio enviado a {} (reserva {})", email, r.getId());
        }
    }
}
