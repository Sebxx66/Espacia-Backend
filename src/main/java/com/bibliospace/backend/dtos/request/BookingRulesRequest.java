package com.bibliospace.backend.dtos.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

// Reglas de reserva que el administrador puede cambiar desde el panel
@Data
public class BookingRulesRequest {
    @NotNull(message = "Indica la hora de apertura")
    @Min(value = 0, message = "La hora de apertura debe estar entre 0 y 22")
    @Max(value = 22, message = "La hora de apertura debe estar entre 0 y 22")
    private Integer horaApertura;

    @NotNull(message = "Indica la hora de cierre")
    @Min(value = 1, message = "La hora de cierre debe estar entre 1 y 23")
    @Max(value = 23, message = "La hora de cierre debe estar entre 1 y 23")
    private Integer horaCierre;

    @NotNull(message = "Indica la duración máxima de una reserva")
    @Min(value = 1, message = "La duración máxima debe ser de al menos 1 hora")
    @Max(value = 12, message = "La duración máxima no puede pasar de 12 horas")
    private Integer maxHorasReserva;

    @NotNull(message = "Indica el máximo de reservas activas")
    @Min(value = 1, message = "Debe permitirse al menos 1 reserva activa")
    @Max(value = 20, message = "El máximo de reservas activas no puede pasar de 20")
    private Integer maxReservasActivas;

    @NotNull(message = "Indica el máximo de equipos por día")
    @Min(value = 0, message = "El máximo de equipos por día no puede ser negativo")
    @Max(value = 10, message = "El máximo de equipos por día no puede pasar de 10")
    private Integer maxEquiposDia;

    @NotNull(message = "Indica el máximo de salas o talleres por día")
    @Min(value = 0, message = "El máximo de salas por día no puede ser negativo")
    @Max(value = 10, message = "El máximo de salas por día no puede pasar de 10")
    private Integer maxSalasDia;

    @NotNull(message = "Indica con cuántas horas de anticipación se puede cancelar")
    @Min(value = 0, message = "Las horas para cancelar no pueden ser negativas")
    @Max(value = 72, message = "Las horas para cancelar no pueden pasar de 72")
    private Integer horasCancelacion;

    @NotNull(message = "Indica los minutos para liberar una reserva no asistida")
    @Min(value = 5, message = "Los minutos para liberar una reserva deben ser al menos 5")
    @Max(value = 240, message = "Los minutos para liberar una reserva no pueden pasar de 240")
    private Integer minutosNoAsistencia;

    @NotNull(message = "Indica con cuántos días de anticipación se puede reservar")
    @Min(value = 1, message = "Debe poder reservarse con al menos 1 día de anticipación")
    @Max(value = 365, message = "Los días de anticipación no pueden pasar de 365")
    private Integer diasAnticipacion;

    // Opcional: si no se envía, se conservan los dominios actuales. Vacío = ninguno.
    @Size(max = 500, message = "La lista de dominios institucionales es demasiado larga")
    private String dominiosInstitucionales;
}
