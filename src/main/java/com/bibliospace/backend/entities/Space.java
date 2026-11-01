package com.bibliospace.backend.entities;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "spaces")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Space {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    // Ej: "SALA_ESTUDIO", "EQUIPO_TECNOLOGICO", "TALLER"
    @Column(nullable = false)
    private String tipo;

    private Integer capacidad;

    // Ej: "ACTIVO", "INACTIVO", "MANTENIMIENTO"
    @Column(nullable = false)
    private String estado;

    // Ej: "Planta 2", "Mostrador central"
    private String ubicacion;

    // Lista separada por comas. Ej: "Proyector, Pizarra, WiFi"
    @Column(columnDefinition = "TEXT")
    private String equipamiento;
}
