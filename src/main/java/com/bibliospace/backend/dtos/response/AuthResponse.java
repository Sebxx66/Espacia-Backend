package com.bibliospace.backend.dtos.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String token;
    private Long id;
    private String nombre;
    private String email;
    private String rol;
}
