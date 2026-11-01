package com.bibliospace.backend.dtos.response;

import java.time.LocalTime;

// Bloque horario ocupado de un espacio
public record SlotResponse(LocalTime horaInicio, LocalTime horaFin) {
}
