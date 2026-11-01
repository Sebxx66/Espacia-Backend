package com.bibliospace.backend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IncidentRequest {
    @NotNull(message = "Selecciona el espacio afectado")
    private Long espacioId;

    @NotBlank(message = "Describe la incidencia")
    @Size(max = 1000, message = "La descripción no puede tener más de 1000 caracteres")
    private String descripcion;

    // Si es true, el espacio pasa a MANTENIMIENTO y se cancelan sus reservas futuras
    private boolean ponerEnMantenimiento;
}
