package com.bibliospace.backend.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResendVerificationRequest {
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    private String email;

    // Dirección desde la que se usa la app, para armar el enlace (se valida en el backend)
    @Size(max = 200)
    private String appUrl;
}
