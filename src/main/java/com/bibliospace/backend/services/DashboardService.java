package com.bibliospace.backend.services;

import com.bibliospace.backend.dtos.response.DashboardResponse;
import com.bibliospace.backend.dtos.response.DashboardResponse.DayCount;
import com.bibliospace.backend.dtos.response.DashboardResponse.HourCount;
import com.bibliospace.backend.dtos.response.DashboardResponse.SpaceCount;
import com.bibliospace.backend.entities.IncidentStatus;
import com.bibliospace.backend.entities.Reservation;
import com.bibliospace.backend.entities.ReservationStatus;
import com.bibliospace.backend.entities.Space;
import com.bibliospace.backend.repositories.IncidentRepository;
import com.bibliospace.backend.repositories.ReservationRepository;
import com.bibliospace.backend.repositories.SpaceRepository;
import com.bibliospace.backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final Locale ES = Locale.of("es");

    private final ReservationRepository reservationRepository;
    private final SpaceRepository spaceRepository;
    private final UserRepository userRepository;
    private final IncidentRepository incidentRepository;

    @Transactional(readOnly = true)
    public DashboardResponse get() {
        LocalDate hoy = LocalDate.now();
        LocalDateTime ahora = LocalDateTime.now();
        // Últimos 30 días hasta la próxima semana, suficiente para todas las métricas
        List<Reservation> recientes = reservationRepository.findByFechaBetween(hoy.minusDays(30), hoy.plusDays(30));
        List<Space> espacios = spaceRepository.findAll();
        long espaciosActivos = espacios.stream().filter(s -> "ACTIVO".equals(s.getEstado())).count();

        long reservasActivas = recientes.stream().filter(r -> ReservationService.vigente(r, ahora)).count();
        long reservasHoy = recientes.stream()
                .filter(r -> r.getFecha().equals(hoy) && r.getEstado().cuentaComoUso()).count();
        long cancelacionesMes = recientes.stream()
                .filter(r -> r.getFecha().getMonth() == hoy.getMonth() && r.getFecha().getYear() == hoy.getYear())
                .filter(r -> r.getEstado() == ReservationStatus.CANCELADA || r.getEstado() == ReservationStatus.RECHAZADA)
                .count();

        // Horas reservadas hoy sobre las horas disponibles (espacios activos x 12 h de apertura)
        long minutosHoy = recientes.stream()
                .filter(r -> r.getFecha().equals(hoy) && r.getEstado().cuentaComoUso())
                .mapToLong(r -> Duration.between(r.getHoraInicio(), r.getHoraFin()).toMinutes())
                .sum();
        int utilizacionHoy = espaciosActivos == 0 ? 0
                : (int) Math.min(100, Math.round(minutosHoy * 100.0 / (espaciosActivos * 12 * 60)));

        List<DayCount> porDia = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate dia = hoy.minusDays(i);
            long n = recientes.stream().filter(r -> r.getFecha().equals(dia) && r.getEstado().cuentaComoUso()).count();
            String etiqueta = dia.getDayOfWeek().getDisplayName(TextStyle.SHORT, ES);
            porDia.add(new DayCount(capitalize(etiqueta.replace(".", "")), dia, n));
        }

        List<Reservation> ultimoMes = recientes.stream()
                .filter(r -> !r.getFecha().isAfter(hoy) && r.getEstado().cuentaComoUso())
                .toList();
        List<HourCount> porHora = new ArrayList<>();
        for (int h = 8; h < 20; h++) {
            LocalTime desde = LocalTime.of(h, 0);
            LocalTime hasta = desde.plusHours(1);
            long n = ultimoMes.stream()
                    .filter(r -> r.getHoraInicio().isBefore(hasta) && r.getHoraFin().isAfter(desde))
                    .count();
            porHora.add(new HourCount(String.format("%02d:00", h), n));
        }

        Map<Long, Space> espaciosPorId = espacios.stream().collect(Collectors.toMap(Space::getId, Function.identity()));
        Map<Long, Long> conteo = recientes.stream()
                .filter(r -> r.getEstado().cuentaComoUso())
                .collect(Collectors.groupingBy(r -> r.getEspacio().getId(), Collectors.counting()));
        long max = conteo.values().stream().mapToLong(Long::longValue).max().orElse(1);
        List<SpaceCount> top = conteo.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .filter(e -> espaciosPorId.containsKey(e.getKey()))
                .map(e -> {
                    Space s = espaciosPorId.get(e.getKey());
                    return new SpaceCount(s.getId(), s.getNombre(), s.getTipo(), e.getValue(),
                            (int) Math.round(e.getValue() * 100.0 / max));
                })
                .toList();

        return new DashboardResponse(
                reservasActivas,
                reservasHoy,
                userRepository.count(),
                espaciosActivos,
                espacios.size(),
                cancelacionesMes,
                incidentRepository.countByEstado(IncidentStatus.ABIERTA),
                utilizacionHoy,
                porDia,
                porHora,
                top,
                reservationRepository.findTop6ByOrderByIdDesc().stream().map(ReservationService::toResponse).toList()
        );
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
