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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Cada minuto libera las reservas de hoy cuya llegada nadie validó dentro del margen (30 minutos por defecto):
// pasan a NO_ASISTIDA, el horario queda libre y el usuario recibe un aviso.
@Slf4j
@Service
@RequiredArgsConstructor
public class NoShowService {

    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    @Value("${app.no-show-minutes:30}")
    private int minutos;

    @Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
    @Transactional
    public int liberarNoAsistidas() {
        LocalDateTime ahora = LocalDateTime.now();
        // Solo las de hoy: las de días anteriores las cierra el personal ("Cerrar como no asistida")
        List<Reservation> vencidas = reservationRepository.findByFecha(LocalDate.now()).stream()
                .filter(r -> r.getEstado() == ReservationStatus.CONFIRMADA || r.getEstado() == ReservationStatus.PENDIENTE)
                .filter(r -> !r.inicio().plusMinutes(minutos).isAfter(ahora))
                .toList();
        vencidas.forEach(reservationService::marcarNoAsistida);
        if (!vencidas.isEmpty()) {
            log.info("{} reserva(s) liberada(s) por no asistencia", vencidas.size());
        }
        return vencidas.size();
    }
}
