package com.bibliospace.backend.dtos.response;

import lombok.Builder;
import lombok.Data;

// Respuesta del registro: la cuenta queda creada pero no puede entrar hasta confirmar el correo
@Data
@Builder
public class RegisterResponse {
    private String email;
    private boolean verificacionPendiente;
    private boolean institucional;
}
