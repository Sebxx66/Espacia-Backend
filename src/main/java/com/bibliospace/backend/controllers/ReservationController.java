package com.bibliospace.backend.controllers;

import com.bibliospace.backend.dtos.request.ReservationRequest;
import com.bibliospace.backend.dtos.response.ReservationResponse;
import com.bibliospace.backend.services.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private static final String PERSONAL = "hasAnyAuthority('ROLE_FUNCIONARIO', 'ROLE_ADMIN')";

    private final ReservationService reservationService;

    // --- Cualquier usuario autenticado ---

    @PostMapping
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.ok(reservationService.create(request));
    }

    @GetMapping("/me")
    public ResponseEntity<List<ReservationResponse>> mine() {
        return ResponseEntity.ok(reservationService.mine());
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ReservationResponse> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.cancel(id));
    }

    @PatchMapping("/{id}/extend")
    public ResponseEntity<ReservationResponse> extend(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.extend(id));
    }

    // --- Funcionarios y administradores ---

    @GetMapping
    @PreAuthorize(PERSONAL)
    public ResponseEntity<List<ReservationResponse>> list(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(reservationService.list(estado, fecha));
    }

    // Valida la llegada del usuario: CONFIRMADA -> EN_USO
    @PatchMapping("/{id}/check-in")
    @PreAuthorize(PERSONAL)
    public ResponseEntity<ReservationResponse> checkIn(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.checkIn(id));
    }

    // Registra el fin del uso o la devolución del equipo: EN_USO -> FINALIZADA
    @PatchMapping("/{id}/finish")
    @PreAuthorize(PERSONAL)
    public ResponseEntity<ReservationResponse> finish(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.finish(id));
    }

    // Anula una reserva que todavía no se usará
    @PatchMapping("/{id}/reject")
    @PreAuthorize(PERSONAL)
    public ResponseEntity<ReservationResponse> reject(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.reject(id));
    }

    // Marca que el usuario no se presentó (solo cuando la reserva ya empezó)
    @PatchMapping("/{id}/no-show")
    @PreAuthorize(PERSONAL)
    public ResponseEntity<ReservationResponse> noShow(@PathVariable Long id) {
        return ResponseEntity.ok(reservationService.noShow(id));
    }
}
