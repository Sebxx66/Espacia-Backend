package com.espacia.backend.controllers;

import com.espacia.backend.dtos.request.BookingRulesRequest;
import com.espacia.backend.entities.BookingRules;
import com.espacia.backend.services.BookingRulesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rules")
@RequiredArgsConstructor
public class BookingRulesController {

    private final BookingRulesService bookingRulesService;

    // Público: la pantalla de inicio y los Términos de uso muestran las reglas vigentes
    @GetMapping
    public ResponseEntity<BookingRules> get() {
        return ResponseEntity.ok(bookingRulesService.get());
    }

    @PutMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<BookingRules> update(@Valid @RequestBody BookingRulesRequest request) {
        return ResponseEntity.ok(bookingRulesService.update(request));
    }
}
