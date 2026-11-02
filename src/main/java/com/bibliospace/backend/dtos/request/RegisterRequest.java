package com.bibliospace.backend.dtos.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede tener más de 100 caracteres")
    private String nombre;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 72, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;

    // Autorización para el tratamiento de datos personales (Ley 1581 de 2012): sin ella no se crea la cuenta
    @AssertTrue(message = "Debes aceptar los Términos de uso y autorizar el tratamiento de tus datos para crear tu cuenta.")
    private boolean aceptaPrivacidad;

    // Versión de la Política de privacidad que el usuario aceptó
    @Size(max = 20, message = "Versión de la política no válida")
    private String versionPolitica;
}
