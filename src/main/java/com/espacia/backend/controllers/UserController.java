package com.espacia.backend.controllers;

import com.espacia.backend.dtos.request.PasswordResetRequest;
import com.espacia.backend.dtos.request.UserCreateRequest;
import com.espacia.backend.dtos.request.UserUpdateRequest;
import com.espacia.backend.dtos.response.UserResponse;
import com.espacia.backend.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Gestión de usuarios: solo administradores
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<UserResponse>> list() {
        return ResponseEntity.ok(userService.list());
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.ok(userService.create(request));
    }

    // Cambia el rol y/o suspende o reactiva la cuenta
    @PatchMapping("/{id}")
    public ResponseEntity<UserResponse> update(@PathVariable Long id, @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    // Asigna una contraseña nueva (para usuarios que olvidaron la suya)
    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordResetRequest request) {
        userService.resetPassword(id, request);
        return ResponseEntity.noContent().build();
    }

    // Marca el correo como confirmado sin esperar el enlace
    @PatchMapping("/{id}/verify-email")
    public ResponseEntity<UserResponse> verifyEmail(@PathVariable Long id) {
        return ResponseEntity.ok(userService.verifyEmail(id));
    }
}
