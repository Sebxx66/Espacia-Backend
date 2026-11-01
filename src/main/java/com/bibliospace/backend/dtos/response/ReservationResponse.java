package com.bibliospace.backend.dtos.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
public class ReservationResponse {
    private Long id;
    // Código legible para el usuario. Ej: R-2026-0042
    private String codigo;
    private Long espacioId;
    private String espacioNombre;
    private String espacioTipo;
    private String espacioUbicacion;
    private Long usuarioId;
    private String usuarioNombre;
    private String usuarioEmail;
    private LocalDate fecha;
    private LocalTime horaInicio;
    private LocalTime horaFin;
    private String estado;
    private String motivo;
    // La hora de fin ya pasó y la reserva sigue confirmada o en uso
    private boolean vencida;
    private LocalDateTime creadaEn;
}
