package com.bibliospace.backend.dtos.response;

import java.time.LocalDateTime;

public record IncidentResponse(
        Long id,
        Long espacioId,
        String espacioNombre,
        String espacioEstado,
        String descripcion,
        String estado,
        String reportadoPor,
        LocalDateTime creadaEn,
        LocalDateTime resueltaEn
) {
}
