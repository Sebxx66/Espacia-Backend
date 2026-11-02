package com.bibliospace.backend.services;

import com.bibliospace.backend.dtos.request.IncidentRequest;
import com.bibliospace.backend.dtos.response.IncidentResponse;
import com.bibliospace.backend.entities.Incident;
import com.bibliospace.backend.entities.IncidentStatus;
import com.bibliospace.backend.entities.Space;
import com.bibliospace.backend.exceptions.ApiException;
import com.bibliospace.backend.repositories.IncidentRepository;
import com.bibliospace.backend.repositories.SpaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final SpaceRepository spaceRepository;
    private final CurrentUserService currentUserService;
    private final ReservationService reservationService;
    private final AuditService auditService;

    private static IncidentResponse toResponse(Incident i) {
        return new IncidentResponse(i.getId(), i.getEspacio().getId(), i.getEspacio().getNombre(),
                i.getEspacio().getEstado(), i.getDescripcion(), i.getEstado().name(),
                i.getReportadoPor().getNombre(), i.getCreadaEn(), i.getResueltaEn());
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> list() {
        return incidentRepository.findAllByOrderByIdDesc().stream().map(IncidentService::toResponse).toList();
    }

    @Transactional
    public IncidentResponse create(IncidentRequest request) {
        Space espacio = spaceRepository.findById(request.getEspacioId())
                .orElseThrow(() -> ApiException.notFound("Espacio no encontrado."));

        if (request.isPonerEnMantenimiento() && !"MANTENIMIENTO".equals(espacio.getEstado())) {
            espacio.setEstado("MANTENIMIENTO");
            reservationService.cancelFutureForSpace(espacio, "el espacio entró en mantenimiento.");
        }

        Incident incident = incidentRepository.save(Incident.builder()
                .espacio(espacio)
                .reportadoPor(currentUserService.get())
                .descripcion(request.getDescripcion().trim())
                .estado(IncidentStatus.ABIERTA)
                .build());
        auditService.registrar("INCIDENCIA_REGISTRADA", "Reportó una incidencia en " + espacio.getNombre()
                + (request.isPonerEnMantenimiento() ? " y lo puso en mantenimiento" : "") + ": " + incident.getDescripcion());
        return toResponse(incident);
    }

    // Al resolver, el espacio puede volver a estar disponible
    @Transactional
    public IncidentResponse resolve(Long id, boolean reactivarEspacio) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Incidencia no encontrada."));
        if (incident.getEstado() == IncidentStatus.RESUELTA) {
            throw ApiException.badRequest("Esta incidencia ya está resuelta.");
        }
        incident.setEstado(IncidentStatus.RESUELTA);
        incident.setResueltaEn(LocalDateTime.now());
        if (reactivarEspacio && "MANTENIMIENTO".equals(incident.getEspacio().getEstado())) {
            incident.getEspacio().setEstado("ACTIVO");
        }
        auditService.registrar("INCIDENCIA_RESUELTA", "Resolvió la incidencia de " + incident.getEspacio().getNombre()
                + (reactivarEspacio ? " y habilitó el espacio" : ""));
        return toResponse(incident);
    }
}
