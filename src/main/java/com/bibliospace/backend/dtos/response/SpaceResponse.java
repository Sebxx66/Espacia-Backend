package com.bibliospace.backend.dtos.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalTime;
import java.util.List;

@Data
@Builder
public class SpaceResponse {
    private Long id;
    private String nombre;
    private String descripcion;
    private String tipo;
    private Integer capacidad;
    private String estado;
    private String ubicacion;
    private List<String> equipamiento;

    // Hay una reserva en curso en este momento
    private boolean ocupadoAhora;
    private LocalTime ocupadoHasta;
}
