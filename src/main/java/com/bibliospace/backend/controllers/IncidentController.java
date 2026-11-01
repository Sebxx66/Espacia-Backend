package com.bibliospace.backend.controllers;

import com.bibliospace.backend.dtos.request.IncidentRequest;
import com.bibliospace.backend.dtos.response.IncidentResponse;
import com.bibliospace.backend.services.IncidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Soporte técnico: reportes de daños o fallas en los espacios
@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ROLE_FUNCIONARIO', 'ROLE_ADMIN')")
public class IncidentController {

    private final IncidentService incidentService;

    @GetMapping
    public ResponseEntity<List<IncidentResponse>> list() {
        return ResponseEntity.ok(incidentService.list());
    }

    @PostMapping
    public ResponseEntity<IncidentResponse> create(@Valid @RequestBody IncidentRequest request) {
        return ResponseEntity.ok(incidentService.create(request));
    }

    @PatchMapping("/{id}/resolve")
    public ResponseEntity<IncidentResponse> resolve(
            @PathVariable Long id,
            @RequestParam(defaultValue = "true") boolean reactivarEspacio) {
        return ResponseEntity.ok(incidentService.resolve(id, reactivarEspacio));
    }
}
