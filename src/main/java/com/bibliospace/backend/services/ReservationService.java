package com.bibliospace.backend.services;

import com.bibliospace.backend.dtos.request.ReservationRequest;
import com.bibliospace.backend.dtos.response.ReservationResponse;
import com.bibliospace.backend.entities.BookingRules;
import com.bibliospace.backend.entities.Reservation;
import com.bibliospace.backend.entities.ReservationStatus;
import com.bibliospace.backend.entities.Space;
import com.bibliospace.backend.entities.User;
import com.bibliospace.backend.exceptions.ApiException;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ReservationService {

    // Las reglas de uso (horario, límites, cancelación...) se configuran desde el panel: BookingRulesService
    private static final String TIPO_EQUIPO = "EQUIPO_TECNOLOGICO";

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d MMM", Locale.of("es"));

    private final BookingRulesService bookingRulesService;
    private final ReservationRepository reservationRepository;
    private final SpaceRepository spaceRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public static String codigo(Reservation r) {
        return String.format("R-%d-%04d", r.getFecha().getYear(), r.getId());
    }

    // Reserva que sigue ocupando el horario y cuya hora de fin todavía no pasó
    public static boolean vigente(Reservation r, LocalDateTime ahora) {
        return r.getEstado().ocupaHorario() && r.fin().isAfter(ahora);
    }

    public static ReservationResponse toResponse(Reservation r) {
        return ReservationResponse.builder()
                .id(r.getId())
                .codigo(codigo(r))
                .espacioId(r.getEspacio().getId())
                .espacioNombre(r.getEspacio().getNombre())
                .espacioTipo(r.getEspacio().getTipo())
                .espacioUbicacion(r.getEspacio().getUbicacion())
                .usuarioId(r.getUsuario().getId())
                .usuarioNombre(r.getUsuario().getNombre())
                .usuarioEmail(r.getUsuario().getEmail())
                .fecha(r.getFecha())
                .horaInicio(r.getHoraInicio())
                .horaFin(r.getHoraFin())
                .estado(r.getEstado().name())
                .motivo(r.getMotivo())
                .vencida((r.getEstado() == ReservationStatus.CONFIRMADA || r.getEstado() == ReservationStatus.EN_USO
                        || r.getEstado() == ReservationStatus.PENDIENTE) && !r.fin().isAfter(LocalDateTime.now()))
                .creadaEn(r.getCreadaEn())
                .build();
    }

    private static String descripcion(Reservation r) {
        return String.format("%s (%s, %s–%s)", r.getEspacio().getNombre(), r.getFecha().format(FECHA),
                r.getHoraInicio(), r.getHoraFin());
    }

    // ---------------------------------------------------------------------
    // Estudiantes (y cualquier usuario autenticado)
    // ---------------------------------------------------------------------

    @Transactional
    public ReservationResponse create(ReservationRequest request) {
        // Bloqueos siempre en el mismo orden (usuario y luego espacio) para evitar interbloqueos:
        // las validaciones de abajo ven las reservas que otra petición acaba de guardar
        User usuario = userRepository.findByIdForUpdate(currentUserService.get().getId())
                .orElseThrow(() -> ApiException.notFound("Usuario no encontrado."));
        Space espacio = spaceRepository.findByIdForUpdate(request.getEspacioId())
                .orElseThrow(() -> ApiException.notFound("Espacio no encontrado."));
        if (!"ACTIVO".equals(espacio.getEstado())) {
            throw ApiException.badRequest("Este espacio no está disponible para reservas en este momento.");
        }

        LocalDate fecha = request.getFecha();
        LocalTime inicio = request.getHoraInicio();
        LocalTime fin = request.getHoraFin();
        LocalDateTime ahora = LocalDateTime.now();
        BookingRules reglas = bookingRulesService.get();

        validarHorario(inicio, fin, reglas);
        if (!LocalDateTime.of(fecha, inicio).isAfter(ahora)) {
            throw ApiException.badRequest("No puedes reservar en una fecha u hora que ya pasó.");
        }
        if (fecha.isAfter(LocalDate.now().plusDays(reglas.getDiasAnticipacion()))) {
            throw ApiException.badRequest("Solo puedes reservar con hasta " + reglas.getDiasAnticipacion() + " días de anticipación.");
        }

        List<Reservation> mias = reservationRepository.findByUsuarioId(usuario.getId());
        long activas = mias.stream().filter(r -> vigente(r, ahora)).count();
        if (activas >= reglas.getMaxReservasActivas()) {
            throw ApiException.badRequest("Ya tienes " + reglas.getMaxReservasActivas()
                    + " reservas activas. Cancela o termina una antes de crear otra.");
        }
        boolean esEquipo = TIPO_EQUIPO.equalsIgnoreCase(espacio.getTipo());
        long delMismoTipoEseDia = mias.stream()
                .filter(r -> r.getFecha().equals(fecha)
                        && r.getEstado() != ReservationStatus.CANCELADA
                        && r.getEstado() != ReservationStatus.RECHAZADA
                        && TIPO_EQUIPO.equalsIgnoreCase(r.getEspacio().getTipo()) == esEquipo)
                .count();
        int limiteDia = esEquipo ? reglas.getMaxEquiposDia() : reglas.getMaxSalasDia();
        if (delMismoTipoEseDia >= limiteDia) {
            throw ApiException.badRequest(limiteDia == 0
                    ? (esEquipo ? "Por ahora no se pueden reservar equipos tecnológicos." : "Por ahora no se pueden reservar salas ni talleres.")
                    : esEquipo
                    ? "Solo puedes reservar " + limiteDia + (limiteDia == 1 ? " equipo tecnológico" : " equipos tecnológicos") + " por día."
                    : "Solo puedes reservar " + limiteDia + (limiteDia == 1 ? " sala o taller" : " salas o talleres") + " por día.");
        }
        boolean choqueMio = mias.stream().anyMatch(r -> r.getEstado().ocupaHorario() && r.getFecha().equals(fecha)
                && seSolapan(r.getHoraInicio(), r.getHoraFin(), inicio, fin));
        if (choqueMio) {
            throw ApiException.conflict("Ya tienes otra reserva en ese mismo horario.");
        }
        if (hayChoque(espacio.getId(), fecha, inicio, fin, null)) {
            throw ApiException.conflict("Ese horario ya está reservado. Elige otro bloque.");
        }

        String motivo = request.getMotivo() == null || request.getMotivo().isBlank() ? null : request.getMotivo().trim();
        Reservation reserva = reservationRepository.save(Reservation.builder()
                .usuario(usuario)
                .espacio(espacio)
                .fecha(fecha)
                .horaInicio(inicio)
                .horaFin(fin)
                .estado(ReservationStatus.CONFIRMADA)
                .motivo(motivo)
                .build());

        notificationService.notify(usuario, "CONFIRMACION",
                "Tu reserva " + codigo(reserva) + " en " + descripcion(reserva) + " quedó confirmada.");
        return toResponse(reserva);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> mine() {
        return reservationRepository.findByUsuarioIdOrderByFechaDescHoraInicioDesc(currentUserService.get().getId())
                .stream().map(ReservationService::toResponse).toList();
    }

    @Transactional
    public ReservationResponse cancel(Long id) {
        Reservation reserva = findMine(id);
        if (reserva.getEstado() != ReservationStatus.CONFIRMADA && reserva.getEstado() != ReservationStatus.PENDIENTE) {
            throw ApiException.badRequest("Solo puedes cancelar reservas confirmadas o pendientes.");
        }
        int horasCancelacion = bookingRulesService.get().getHorasCancelacion();
        if (LocalDateTime.now().isAfter(reserva.inicio().minusHours(horasCancelacion))) {
            throw ApiException.badRequest(horasCancelacion == 0
                    ? "La reserva ya empezó: no se puede cancelar."
                    : "Solo puedes cancelar hasta " + horasCancelacion + (horasCancelacion == 1 ? " hora" : " horas")
                    + " antes del inicio de la reserva.");
        }
        reserva.setEstado(ReservationStatus.CANCELADA);
        notificationService.notify(reserva.getUsuario(), "CANCELACION",
                "Cancelaste la reserva " + codigo(reserva) + " en " + descripcion(reserva) + ".");
        return toResponse(reserva);
    }

    // Suma una hora al final de la reserva si el espacio está libre
    @Transactional
    public ReservationResponse extend(Long id) {
        Reservation reserva = findMine(id);
        // Mismo orden de bloqueo que create(): usuario y luego espacio
        userRepository.findByIdForUpdate(reserva.getUsuario().getId());
        spaceRepository.findByIdForUpdate(reserva.getEspacio().getId());
        if (reserva.getEstado() != ReservationStatus.CONFIRMADA && reserva.getEstado() != ReservationStatus.EN_USO) {
            throw ApiException.badRequest("Solo puedes extender reservas confirmadas o en uso.");
        }
        if (!reserva.fin().isAfter(LocalDateTime.now())) {
            throw ApiException.badRequest("Esta reserva ya terminó.");
        }
        BookingRules reglas = bookingRulesService.get();
        LocalTime cierre = LocalTime.of(reglas.getHoraCierre(), 0);
        LocalTime nuevoFin = reserva.getHoraFin().plusHours(1);
        if (nuevoFin.isAfter(cierre) || nuevoFin.isBefore(reserva.getHoraFin())) {
            throw ApiException.badRequest("No se puede extender más allá de las " + hora(reglas.getHoraCierre()) + ".");
        }
        if (Duration.between(reserva.getHoraInicio(), nuevoFin).toHours() > reglas.getMaxHorasReserva()) {
            throw ApiException.badRequest("Una reserva no puede durar más de " + reglas.getMaxHorasReserva() + " horas.");
        }
        if (hayChoque(reserva.getEspacio().getId(), reserva.getFecha(), reserva.getHoraFin(), nuevoFin, reserva.getId())) {
            throw ApiException.conflict("El espacio ya está reservado justo después de tu horario.");
        }
        // Tampoco puede quedar encima de otra reserva del mismo usuario (por ejemplo, un equipo a continuación)
        LocalTime finActual = reserva.getHoraFin();
        boolean choqueMio = reservationRepository.findByUsuarioId(reserva.getUsuario().getId()).stream()
                .anyMatch(r -> !r.getId().equals(reserva.getId()) && r.getEstado().ocupaHorario()
                        && r.getFecha().equals(reserva.getFecha())
                        && seSolapan(r.getHoraInicio(), r.getHoraFin(), finActual, nuevoFin));
        if (choqueMio) {
            throw ApiException.conflict("Tienes otra reserva justo después de este horario.");
        }
        reserva.setHoraFin(nuevoFin);
        notificationService.notify(reserva.getUsuario(), "EXTENSION",
                "Tu reserva " + codigo(reserva) + " ahora termina a las " + nuevoFin + ".");
        return toResponse(reserva);
    }

    // ---------------------------------------------------------------------
    // Funcionarios y administradores
    // ---------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ReservationResponse> list(String estado, LocalDate fecha) {
        ReservationStatus filtro = parseEstado(estado);
        List<Reservation> base = fecha != null
                ? reservationRepository.findByFecha(fecha)
                : reservationRepository.findAllByOrderByFechaDescHoraInicioDesc();
        return base.stream()
                .filter(r -> filtro == null || r.getEstado() == filtro)
                .sorted((a, b) -> fecha != null
                        ? a.getHoraInicio().compareTo(b.getHoraInicio())
                        : b.inicio().compareTo(a.inicio()))
                .map(ReservationService::toResponse)
                .toList();
    }

    @Transactional
    public ReservationResponse checkIn(Long id) {
        Reservation reserva = find(id);
        if (reserva.getEstado() != ReservationStatus.CONFIRMADA && reserva.getEstado() != ReservationStatus.PENDIENTE) {
            throw ApiException.badRequest("Solo se puede validar una reserva confirmada.");
        }
        if (!reserva.getFecha().equals(LocalDate.now())) {
            throw ApiException.badRequest("Solo se pueden validar reservas del día de hoy.");
        }
        if (!reserva.fin().isAfter(LocalDateTime.now())) {
            throw ApiException.badRequest("El horario de esta reserva ya terminó.");
        }
        reserva.setEstado(ReservationStatus.EN_USO);
        notificationService.notify(reserva.getUsuario(), "CHECK_IN",
                "Se registró tu ingreso a " + descripcion(reserva) + ". ¡Buen trabajo!");
        return toResponse(reserva);
    }

    // Fin del uso: para equipos es la devolución
    @Transactional
    public ReservationResponse finish(Long id) {
        Reservation reserva = find(id);
        if (reserva.getEstado() != ReservationStatus.EN_USO) {
            throw ApiException.badRequest("Solo se pueden finalizar reservas que están en uso.");
        }
        reserva.setEstado(ReservationStatus.FINALIZADA);
        boolean esEquipo = "EQUIPO_TECNOLOGICO".equals(reserva.getEspacio().getTipo());
        notificationService.notify(reserva.getUsuario(), "FINALIZADA", esEquipo
                ? "Se registró la devolución de " + reserva.getEspacio().getNombre() + ". ¡Gracias!"
                : "Finalizó tu uso de " + descripcion(reserva) + ". ¡Gracias!");
        return toResponse(reserva);
    }

    @Transactional
    public ReservationResponse reject(Long id) {
        Reservation reserva = find(id);
        if (reserva.getEstado() != ReservationStatus.CONFIRMADA && reserva.getEstado() != ReservationStatus.PENDIENTE) {
            throw ApiException.badRequest("Solo se pueden anular reservas confirmadas o pendientes.");
        }
        reserva.setEstado(ReservationStatus.RECHAZADA);
        notificationService.notify(reserva.getUsuario(), "RECHAZADA",
                "Tu reserva " + codigo(reserva) + " en " + descripcion(reserva) + " fue anulada por el personal de la biblioteca.");
        auditService.registrar("RESERVA_ANULADA", "Anuló la reserva " + codigo(reserva) + " de "
                + AuditService.persona(reserva.getUsuario()) + " en " + descripcion(reserva));
        return toResponse(reserva);
    }

    // El personal marca que el usuario no llegó: solo cuando la reserva ya empezó
    @Transactional
    public ReservationResponse noShow(Long id) {
        Reservation reserva = find(id);
        if (reserva.getEstado() != ReservationStatus.CONFIRMADA && reserva.getEstado() != ReservationStatus.PENDIENTE) {
            throw ApiException.badRequest("Solo se puede marcar como no asistida una reserva confirmada.");
        }
        if (reserva.inicio().isAfter(LocalDateTime.now())) {
            throw ApiException.badRequest("La reserva todavía no empieza. Si no se usará, anúlala.");
        }
        marcarNoAsistida(reserva);
        auditService.registrar("RESERVA_NO_ASISTIDA", "Marcó como no asistida la reserva " + codigo(reserva) + " de "
                + AuditService.persona(reserva.getUsuario()) + " en " + descripcion(reserva));
        return toResponse(reserva);
    }

    public void marcarNoAsistida(Reservation reserva) {
        reserva.setEstado(ReservationStatus.NO_ASISTIDA);
        notificationService.notify(reserva.getUsuario(), "NO_ASISTIDA",
                "Tu reserva " + codigo(reserva) + " en " + descripcion(reserva)
                        + " se marcó como no asistida porque no se registró tu llegada. El espacio quedó libre para otras personas.");
    }

    // Cuando un espacio pasa a mantenimiento o inactivo, sus reservas futuras se anulan y se avisa a cada usuario
    @Transactional
    public int cancelFutureForSpace(Space espacio, String motivo) {
        LocalDateTime ahora = LocalDateTime.now();
        List<Reservation> futuras = reservationRepository.findByEspacioIdAndFechaGreaterThanEqual(espacio.getId(), LocalDate.now())
                .stream()
                .filter(r -> (r.getEstado() == ReservationStatus.CONFIRMADA || r.getEstado() == ReservationStatus.PENDIENTE)
                        && r.inicio().isAfter(ahora))
                .toList();
        for (Reservation r : futuras) {
            r.setEstado(ReservationStatus.RECHAZADA);
            notificationService.notify(r.getUsuario(), "RECHAZADA",
                    "Tu reserva " + codigo(r) + " en " + descripcion(r) + " fue anulada: " + motivo);
        }
        return futuras.size();
    }

    // ---------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------

    private void validarHorario(LocalTime inicio, LocalTime fin, BookingRules reglas) {
        if (!fin.isAfter(inicio)) {
            throw ApiException.badRequest("La hora de fin debe ser posterior a la hora de inicio.");
        }
        if (inicio.isBefore(LocalTime.of(reglas.getHoraApertura(), 0)) || fin.isAfter(LocalTime.of(reglas.getHoraCierre(), 0))) {
            throw ApiException.badRequest("El horario de reservas es de " + hora(reglas.getHoraApertura())
                    + " a " + hora(reglas.getHoraCierre()) + ".");
        }
        if (Duration.between(inicio, fin).toMinutes() > reglas.getMaxHorasReserva() * 60L) {
            throw ApiException.badRequest("Una reserva no puede durar más de " + reglas.getMaxHorasReserva() + " horas.");
        }
    }

    private static String hora(int h) {
        return String.format("%02d:00", h);
    }

    private boolean hayChoque(Long espacioId, LocalDate fecha, LocalTime inicio, LocalTime fin, Long excluirId) {
        return reservationRepository.findByEspacioIdAndFecha(espacioId, fecha).stream()
                .filter(r -> excluirId == null || !r.getId().equals(excluirId))
                .anyMatch(r -> r.getEstado().ocupaHorario() && seSolapan(r.getHoraInicio(), r.getHoraFin(), inicio, fin));
    }

    private static boolean seSolapan(LocalTime aInicio, LocalTime aFin, LocalTime bInicio, LocalTime bFin) {
        return aInicio.isBefore(bFin) && aFin.isAfter(bInicio);
    }

    private static ReservationStatus parseEstado(String estado) {
        if (estado == null || estado.isBlank()) return null;
        try {
            return ReservationStatus.valueOf(estado.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Estado de reserva inválido.");
        }
    }

    private Reservation find(Long id) {
        return reservationRepository.findById(id).orElseThrow(() -> ApiException.notFound("Reserva no encontrada."));
    }

    // Una reserva ajena se trata como inexistente para no revelar datos de otros usuarios
    private Reservation findMine(Long id) {
        Long userId = currentUserService.get().getId();
        return reservationRepository.findById(id)
                .filter(r -> r.getUsuario().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Reserva no encontrada."));
    }
}
