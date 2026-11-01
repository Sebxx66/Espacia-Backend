package com.bibliospace.backend.dtos.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class ReservationRequest {
    @NotNull(message = "Selecciona un espacio")
    private Long espacioId;

    @NotNull(message = "Selecciona una fecha")
    private LocalDate fecha;

    @NotNull(message = "Selecciona la hora de inicio")
    private LocalTime horaInicio;

    @NotNull(message = "Selecciona la hora de fin")
    private LocalTime horaFin;

    @Size(max = 500, message = "El motivo no puede tener más de 500 caracteres")
    private String motivo;
}
