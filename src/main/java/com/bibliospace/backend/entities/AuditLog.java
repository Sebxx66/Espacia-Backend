package com.bibliospace.backend.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// Registro de auditoría: quién hizo qué en el panel (administradores y funcionarios).
// Guarda el nombre y el correo como texto para que el historial se conserve aunque la cuenta cambie.
@Entity
@Table(name = "audit_log", indexes = @Index(name = "idx_audit_fecha", columnList = "fecha"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;

    @Column(length = 150)
    private String usuarioNombre;

    @Column(length = 255)
    private String usuarioEmail;

    @Column(length = 30)
    private String usuarioRol;

    // Código de la acción, por ejemplo USUARIO_SUSPENDIDO (el frontend lo traduce)
    @Column(nullable = false, length = 40)
    private String accion;

    @Column(nullable = false, length = 500)
    private String detalle;

    // Red desde la que se hizo (IPv4 o IPv6)
    @Column(length = 45)
    private String ip;
}
