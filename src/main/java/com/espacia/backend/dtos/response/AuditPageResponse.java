package com.espacia.backend.dtos.response;

import com.espacia.backend.entities.AuditLog;

import java.util.List;

// Una página del registro de auditoría, del más reciente al más antiguo
public record AuditPageResponse(List<AuditLog> items, long total, int pagina, int tamano, boolean hayMas) {
}
