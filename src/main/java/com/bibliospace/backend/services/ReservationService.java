package com.bibliospace.backend.services;

import com.bibliospace.backend.dtos.request.ReservationRequest;
import com.bibliospace.backend.dtos.response.ReservationResponse;
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

    // Reglas de uso (las mismas que muestra el frontend)
    public static final LocalTime APERTURA = LocalTime.of(8, 0);
    public static final LocalTime CIERRE = LocalTime.of(20, 0);
    public static final int MAX_HORAS = 4;
    public static final int MAX_ACTIVAS = 3;
    // Máximo de reservas por usuario y por día, según el tipo de espacio
    public static final int MAX_EQUIPOS_DIA = 1;
    public static final int MAX_SALAS_DIA = 2;
    private static final String TIPO_EQUIPO = "EQUIPO_TECNOLOGICO";
    public static final int DIAS_ANTICIPACION = 30;
    public static final Duration LIMITE_CANCELACION = Duration.ofHours(1);

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d MMM", Locale.of("es"));

    private final ReservationRepository reservationRepository;
    private final SpaceRepository spaceRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final NotificationService notificationService;

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

        validarHorario(inicio, fin);
        if (!LocalDateTime.of(fecha, inicio).isAfter(ahora)) {
            throw ApiException.badRequest("No puedes reservar en una fecha u hora que ya pasó.");
        }
        if (fecha.isAfter(LocalDate.now().plusDays(DIAS_ANTICIPACION))) {
            throw ApiException.badRequest("Solo puedes reservar con hasta " + DIAS_ANTICIPACION + " días de anticipación.");
        }

        List<Reservation> mias = reservationRepository.findByUsuarioId(usuario.getId());
        long activas = mias.stream().filter(r -> vigente(r, ahora)).count();
        if (activas >= MAX_ACTIVAS) {
            throw ApiException.badRequest("Ya tienes " + MAX_ACTIVAS + " reservas activas. Cancela o termina una antes de crear otra.");
        }
        boolean esEquipo = TIPO_EQUIPO.equalsIgnoreCase(espacio.getTipo());
        long delMismoTipoEseDia = mias.stream()
                .filter(r -> r.getFecha().equals(fecha)
                        && r.getEstado() != ReservationStatus.CANCELADA
                        && r.getEstado() != ReservationStatus.RECHAZADA
                        && TIPO_EQUIPO.equalsIgnoreCase(r.getEspacio().getTipo()) == esEquipo)
                .count();
        if (delMismoTipoEseDia >= (esEquipo ? MAX_EQUIPOS_DIA : MAX_SALAS_DIA)) {
            throw ApiException.badRequest(esEquipo
                    ? "Solo puedes reservar " + MAX_EQUIPOS_DIA + " equipo tecnológico por día."
                    : "Solo puedes reservar " + MAX_SALAS_DIA + " salas o talleres por día.");
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
        if (LocalDateTime.now().isAfter(reserva.inicio().minus(LIMITE_CANCELACION))) {
            throw ApiException.badRequest("Solo puedes cancelar hasta 1 hora antes del inicio de la reserva.");
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
        LocalTime nuevoFin = reserva.getHoraFin().plusHours(1);
        if (nuevoFin.isAfter(CIERRE) || nuevoFin.isBefore(reserva.getHoraFin())) {
            throw ApiException.badRequest("No se puede extender más allá de las 20:00.");
        }
        if (Duration.between(reserva.getHoraInicio(), nuevoFin).toHours() > MAX_HORAS) {
            throw ApiException.badRequest("Una reserva no puede durar más de " + MAX_HORAS + " horas.");
        }
        if (hayChoque(reserva.getEspacio().getId(), reserva.getFecha(), reserva.getHoraFin(), nuevoFin, reserva.getId())) {
            throw ApiException.conflict("El espacio ya está reservado justo después de tu horario.");
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
        return toResponse(reserva);
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

    private void validarHorario(LocalTime inicio, LocalTime fin) {
        if (!fin.isAfter(inicio)) {
            throw ApiException.badRequest("La hora de fin debe ser posterior a la hora de inicio.");
        }
        if (inicio.isBefore(APERTURA) || fin.isAfter(CIERRE)) {
            throw ApiException.badRequest("El horario de reservas es de 08:00 a 20:00.");
        }
        if (Duration.between(inicio, fin).toMinutes() > MAX_HORAS * 60L) {
            throw ApiException.badRequest("Una reserva no puede durar más de " + MAX_HORAS + " horas.");
        }
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
