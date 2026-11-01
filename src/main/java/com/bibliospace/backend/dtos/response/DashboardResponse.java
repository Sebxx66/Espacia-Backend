package com.bibliospace.backend.dtos.response;

import java.time.LocalDate;
import java.util.List;

// Datos del panel del administrador, calculados a partir de las reservas reales
public record DashboardResponse(
        long reservasActivas,
        long reservasHoy,
        long usuariosRegistrados,
        long espaciosActivos,
        long espaciosTotales,
        long cancelacionesMes,
        long incidenciasAbiertas,
        int utilizacionHoy,
        List<DayCount> reservasPorDia,
        List<HourCount> reservasPorHora,
        List<SpaceCount> topEspacios,
        List<ReservationResponse> recientes
) {
    public record DayCount(String etiqueta, LocalDate fecha, long reservas) {
    }

    public record HourCount(String hora, long reservas) {
    }

    public record SpaceCount(Long id, String nombre, String tipo, long reservas, int porcentaje) {
    }
}
