package com.bibliospace.backend.dtos.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String nombre;
    private String email;
    private String rol;
    private Boolean activo;
    // false = se registró y aún no confirma su correo
    private Boolean emailVerificado;
    // Su correo pertenece a un dominio institucional (Configuración)
    private Boolean institucional;
    private Long reservas;
    private LocalDateTime creadoEn;
}
