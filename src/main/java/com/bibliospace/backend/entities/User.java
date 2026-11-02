package com.bibliospace.backend.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role rol;

    // false = cuenta suspendida por el administrador
    @ColumnDefault("true")
    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime creadoEn;

    // Prueba de la autorización de tratamiento de datos: cuándo la aceptó y qué versión de la política
    private LocalDateTime privacidadAceptadaEn;

    @Column(length = 20)
    private String privacidadVersion;

    // Momento del último cambio de contraseña: los tokens emitidos antes dejan de servir
    private Instant passwordCambiadaEn;

    // Guarda la nueva contraseña (ya cifrada) y cierra las sesiones abiertas con la anterior.
    // Se redondea al segundo porque los tokens guardan la hora de emisión en segundos.
    public void cambiarPassword(String hash) {
        this.password = hash;
        this.passwordCambiadaEn = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
