package com.bibliospace.backend.controllers;

import com.bibliospace.backend.dtos.request.ChangePasswordRequest;
import com.bibliospace.backend.dtos.request.ForgotPasswordRequest;
import com.bibliospace.backend.dtos.request.LoginRequest;
import com.bibliospace.backend.dtos.request.RegisterRequest;
import com.bibliospace.backend.dtos.request.ResetPasswordRequest;
import com.bibliospace.backend.dtos.response.AuthResponse;
import com.bibliospace.backend.dtos.response.UserResponse;
import com.bibliospace.backend.services.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // Las cuentas creadas aquí siempre son de ESTUDIANTE
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // Envía un correo con el enlace para elegir una contraseña nueva (responde 204 exista o no el email)
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request.getEmail());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    // Datos del usuario dueño del token
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        return ResponseEntity.ok(authService.me());
    }

    // Cierra las demás sesiones del usuario y devuelve un token nuevo para seguir en este dispositivo
    @PatchMapping("/password")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(Map.of("token", authService.changePassword(request)));
    }
}
