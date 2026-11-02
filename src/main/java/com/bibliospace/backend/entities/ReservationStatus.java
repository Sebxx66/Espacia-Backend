package com.bibliospace.backend.entities;

public enum ReservationStatus {
    PENDIENTE,
    CONFIRMADA,
    RECHAZADA,
    EN_USO,
    FINALIZADA,
    CANCELADA,
    // Nadie validó la llegada a tiempo: el espacio se libera
    NO_ASISTIDA;

    // Estados que bloquean el horario del espacio
    public boolean ocupaHorario() {
        return this == PENDIENTE || this == CONFIRMADA || this == EN_USO;
    }

    // Estados que cuentan como uso real del espacio (para estadísticas)
    public boolean cuentaComoUso() {
        return this == CONFIRMADA || this == EN_USO || this == FINALIZADA;
    }
}
