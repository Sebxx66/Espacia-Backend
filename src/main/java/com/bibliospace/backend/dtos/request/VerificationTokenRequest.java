package com.bibliospace.backend.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerificationTokenRequest {
    @NotBlank(message = "El enlace no es válido.")
    @Size(max = 200, message = "El enlace no es válido.")
    private String token;
}
