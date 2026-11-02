package com.bibliospace.backend.controllers;

import com.bibliospace.backend.dtos.response.AuditPageResponse;
import com.bibliospace.backend.dtos.response.DashboardResponse;
import com.bibliospace.backend.entities.AuditLog;
import com.bibliospace.backend.services.AuditService;
import com.bibliospace.backend.services.DashboardService;
import org.springframework.data.domain.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminDashboardController {

    private final DashboardService dashboardService;
    private final AuditService auditService;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> dashboard() {
        return ResponseEntity.ok(dashboardService.get());
    }

    // Registro de auditoría: quién hizo qué en el panel. Se puede buscar por texto (usuario, acción o detalle).
    @GetMapping("/audit")
    public ResponseEntity<AuditPageResponse> audit(@RequestParam(required = false) String q,
                                                   @RequestParam(defaultValue = "0") int pagina,
                                                   @RequestParam(defaultValue = "50") int tamano) {
        Page<AuditLog> page = auditService.listar(q, pagina, tamano);
        return ResponseEntity.ok(new AuditPageResponse(page.getContent(), page.getTotalElements(),
                page.getNumber(), page.getSize(), page.hasNext()));
    }
}
