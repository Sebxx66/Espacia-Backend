package com.bibliospace.backend.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "reservations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relación: Muchos reservas pueden pertenecer a un usuario
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User usuario;

    // Relación: Muchas reservas pueden hacerse sobre un mismo espacio
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "space_id", nullable = false)
    private Space espacio;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime horaInicio;

    @Column(nullable = false)
    private LocalTime horaFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus estado;

    @Column(length = 500)
    private String motivo;

    // true cuando ya se envió (o se descartó) el recordatorio por correo
    @ColumnDefault("false")
    @Column(nullable = false)
    @Builder.Default
    private Boolean recordatorioEnviado = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime creadaEn;

    public LocalDateTime inicio() {
        return LocalDateTime.of(fecha, horaInicio);
    }

    public LocalDateTime fin() {
        return LocalDateTime.of(fecha, horaFin);
    }
}
