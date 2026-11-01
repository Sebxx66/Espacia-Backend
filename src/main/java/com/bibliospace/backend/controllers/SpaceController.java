package com.bibliospace.backend.controllers;

import com.bibliospace.backend.dtos.request.SpaceRequest;
import com.bibliospace.backend.dtos.response.SlotResponse;
import com.bibliospace.backend.dtos.response.SpaceResponse;
import com.bibliospace.backend.services.SpaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
// Importante: Usamos PreAuthorize para proteger rutas según el rol
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/spaces")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;

    // Cualquier usuario autenticado (ESTUDIANTE, FUNCIONARIO, ADMIN) puede ver el catálogo
    @GetMapping
    public ResponseEntity<List<SpaceResponse>> getAllSpaces() {
        return ResponseEntity.ok(spaceService.getAllSpaces());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SpaceResponse> getSpaceById(@PathVariable Long id) {
        return ResponseEntity.ok(spaceService.getSpaceById(id));
    }

    // Horarios ya ocupados de un espacio en una fecha (para el calendario de reservas)
    @GetMapping("/{id}/availability")
    public ResponseEntity<List<SlotResponse>> availability(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(spaceService.availability(id, fecha));
    }

    // SOLO un ADMIN puede crear, editar o eliminar espacios
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<SpaceResponse> createSpace(@Valid @RequestBody SpaceRequest request) {
        return ResponseEntity.ok(spaceService.createSpace(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<SpaceResponse> updateSpace(@PathVariable Long id, @Valid @RequestBody SpaceRequest request) {
        return ResponseEntity.ok(spaceService.updateSpace(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteSpace(@PathVariable Long id) {
        spaceService.deleteSpace(id);
        return ResponseEntity.noContent().build();
    }
}
