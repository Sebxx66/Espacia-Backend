package com.bibliospace.backend.dtos.response;

import java.time.LocalDateTime;

public record NotificationResponse(Long id, String mensaje, String tipo, boolean leida, LocalDateTime creadaEn) {
}
