package com.bibliospace.backend.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Reglas de reserva de la biblioteca. Hay una sola fila (id = 1) que el administrador edita desde el panel.
@Entity
@Table(name = "booking_rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingRules {

    public static final long ID = 1L;
    public static final String DOMINIOS_POR_DEFECTO = "amigo.edu.co";

    @Id
    private Long id;

    // Horario de reservas (horas en punto, 0 a 23)
    @Column(nullable = false)
    private Integer horaApertura;

    @Column(nullable = false)
    private Integer horaCierre;

    @Column(nullable = false)
    private Integer maxHorasReserva;

    @Column(nullable = false)
    private Integer maxReservasActivas;

    // Máximo por usuario y por día, según el tipo de espacio
    @Column(nullable = false)
    private Integer maxEquiposDia;

    @Column(nullable = false)
    private Integer maxSalasDia;

    // Se puede cancelar hasta estas horas antes del inicio
    @Column(nullable = false)
    private Integer horasCancelacion;

    // Minutos después del inicio sin validar la llegada para liberar la reserva
    @Column(nullable = false)
    private Integer minutosNoAsistencia;

    @Column(nullable = false)
    private Integer diasAnticipacion;

    // Dominios de correo de la institución, separados por coma (ej: "amigo.edu.co").
    // Sirven para identificar a los usuarios institucionales; también cuentan sus subdominios.
    @Column(length = 500)
    private String dominiosInstitucionales;

    private LocalDateTime actualizadoEn;

    // No se publica: GET /rules es público
    @JsonIgnore
    private String actualizadoPor;

    // Valores con los que arranca una instalación nueva
    public static BookingRules defaults() {
        return BookingRules.builder()
                .id(ID)
                .horaApertura(8)
                .horaCierre(20)
                .maxHorasReserva(4)
                .maxReservasActivas(3)
                .maxEquiposDia(1)
                .maxSalasDia(2)
                .horasCancelacion(1)
                .minutosNoAsistencia(30)
                .diasAnticipacion(30)
                .dominiosInstitucionales(DOMINIOS_POR_DEFECTO)
                .build();
    }
}
