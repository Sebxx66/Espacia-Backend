package com.bibliospace.backend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// Activa la cuenta: el token llega en el enlace del correo y la contraseña la escribe la persona.
// Pedir la contraseña evita que alguien active una cuenta creada por otro con su correo.
@Data
public class VerifyEmailRequest {
    @NotBlank(message = "El enlace no es válido.")
    @Size(max = 200, message = "El enlace no es válido.")
    private String token;

    @NotBlank(message = "Escribe tu contraseña.")
    @Size(max = 72, message = "Contraseña no válida.")
    private String password;
}
