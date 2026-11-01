package com.bibliospace.backend.services;

import com.bibliospace.backend.dtos.request.SpaceRequest;
import com.bibliospace.backend.dtos.response.SlotResponse;
import com.bibliospace.backend.dtos.response.SpaceResponse;
import com.bibliospace.backend.entities.Reservation;
import com.bibliospace.backend.entities.Space;
import com.bibliospace.backend.exceptions.ApiException;
import com.bibliospace.backend.repositories.IncidentRepository;
import com.bibliospace.backend.repositories.ReservationRepository;
import com.bibliospace.backend.repositories.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpaceService {

    private final SpaceRepository spaceRepository;
    private final ReservationRepository reservationRepository;
    private final IncidentRepository incidentRepository;
    private final ReservationService reservationService;

    @Transactional(readOnly = true)
    public List<SpaceResponse> getAllSpaces() {
        Map<Long, LocalTime> ocupados = ocupadosAhora();
        return spaceRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(Space::getNombre, String.CASE_INSENSITIVE_ORDER))
                .map(space -> mapToResponse(space, ocupados.get(space.getId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SpaceResponse getSpaceById(Long id) {
        return mapToResponse(find(id), ocupadosAhora().get(id));
    }

    // Bloques ya reservados de un espacio en una fecha
    @Transactional(readOnly = true)
    public List<SlotResponse> availability(Long id, LocalDate fecha) {
        find(id);
        return reservationRepository.findByEspacioIdAndFecha(id, fecha).stream()
                .filter(r -> r.getEstado().ocupaHorario())
                .sorted(Comparator.comparing(Reservation::getHoraInicio))
                .map(r -> new SlotResponse(r.getHoraInicio(), r.getHoraFin()))
                .toList();
    }

    @Transactional
    public SpaceResponse createSpace(SpaceRequest request) {
        Space space = new Space();
        apply(space, request);
        Space savedSpace = spaceRepository.save(space);
        return mapToResponse(savedSpace, null);
    }

    @Transactional
    public SpaceResponse updateSpace(Long id, SpaceRequest request) {
        Space space = find(id);
        String estadoAnterior = space.getEstado();
        apply(space, request);
        if ("ACTIVO".equals(estadoAnterior) && !"ACTIVO".equals(space.getEstado())) {
            String motivo = "MANTENIMIENTO".equals(space.getEstado())
                    ? "el espacio entró en mantenimiento."
                    : "el espacio ya no está disponible.";
            reservationService.cancelFutureForSpace(space, motivo);
        }
        return mapToResponse(space, ocupadosAhora().get(id));
    }

    @Transactional
    public void deleteSpace(Long id) {
        Space space = find(id);
        if (reservationRepository.existsByEspacioId(id) || incidentRepository.existsByEspacioId(id)) {
            throw ApiException.conflict("Este espacio tiene reservas o incidencias registradas. Márcalo como inactivo en lugar de eliminarlo.");
        }
        spaceRepository.delete(space);
    }

    private void apply(Space space, SpaceRequest request) {
        space.setNombre(request.getNombre().trim());
        space.setDescripcion(blankToNull(request.getDescripcion()));
        // "Sala de estudio" -> "SALA_DE_ESTUDIO"
        space.setTipo(request.getTipo().trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "_"));
        space.setCapacidad(request.getCapacidad());
        space.setEstado(request.getEstado());
        space.setUbicacion(blankToNull(request.getUbicacion()));
        space.setEquipamiento(request.getEquipamiento() == null ? null : blankToNull(request.getEquipamiento().stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "))));
    }

    // id de espacio -> hora en que termina la reserva que lo ocupa ahora mismo
    private Map<Long, LocalTime> ocupadosAhora() {
        LocalDateTime ahora = LocalDateTime.now();
        Map<Long, LocalTime> ocupados = new HashMap<>();
        reservationRepository.findByFecha(LocalDate.now()).stream()
                .filter(r -> r.getEstado().ocupaHorario() && !r.inicio().isAfter(ahora) && r.fin().isAfter(ahora))
                .forEach(r -> ocupados.put(r.getEspacio().getId(), r.getHoraFin()));
        return ocupados;
    }

    private Space find(Long id) {
        return spaceRepository.findById(id).orElseThrow(() -> ApiException.notFound("Espacio no encontrado."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private SpaceResponse mapToResponse(Space space, LocalTime ocupadoHasta) {
        List<String> equipamiento = space.getEquipamiento() == null ? List.of()
                : Arrays.stream(space.getEquipamiento().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
        return SpaceResponse.builder()
                .id(space.getId())
                .nombre(space.getNombre())
                .descripcion(space.getDescripcion())
                .tipo(space.getTipo())
                .capacidad(space.getCapacidad())
                .estado(space.getEstado())
                .ubicacion(space.getUbicacion())
                .equipamiento(equipamiento)
                .ocupadoAhora(ocupadoHasta != null)
                .ocupadoHasta(ocupadoHasta)
                .build();
    }
}
