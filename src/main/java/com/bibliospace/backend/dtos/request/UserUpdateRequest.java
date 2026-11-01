package com.bibliospace.backend.dtos.request;

import lombok.Data;

// Campos opcionales: solo se cambia lo que venga distinto de null
@Data
public class UserUpdateRequest {
    private String rol;
    private Boolean activo;
}
