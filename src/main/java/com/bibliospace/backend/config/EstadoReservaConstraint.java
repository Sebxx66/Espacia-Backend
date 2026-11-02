package com.bibliospace.backend.config;

import com.bibliospace.backend.entities.ReservationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.Arrays;
import java.util.stream.Collectors;

// Hibernate crea en PostgreSQL una restricción CHECK con los estados de reserva que existían al crear la tabla,
// y "ddl-auto=update" no la actualiza cuando se agrega un estado nuevo (por ejemplo NO_ASISTIDA).
// Al arrancar se reemplaza por una que incluye todos los estados actuales. Es seguro repetirlo.
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class EstadoReservaConstraint implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (!esPostgres()) {
                return;
            }
            String estados = Arrays.stream(ReservationStatus.values())
                    .map(e -> "'" + e.name() + "'")
                    .collect(Collectors.joining(", "));
            jdbc.execute("""
                    DO $$
                    DECLARE c record;
                    BEGIN
                      IF to_regclass('reservations') IS NULL THEN RETURN; END IF;
                      FOR c IN SELECT conname FROM pg_constraint
                               WHERE conrelid = 'reservations'::regclass AND contype = 'c'
                                 AND pg_get_constraintdef(oid) LIKE '%estado%'
                      LOOP
                        EXECUTE format('ALTER TABLE reservations DROP CONSTRAINT %I', c.conname);
                      END LOOP;
                      ALTER TABLE reservations ADD CONSTRAINT reservations_estado_check CHECK (estado IN (ESTADOS));
                    END $$
                    """.replace("ESTADOS", estados));
        } catch (Exception e) {
            log.warn("No se pudo actualizar la restricción de estados de reserva: {}", e.getMessage());
        }
    }

    private boolean esPostgres() {
        Boolean pg = jdbc.execute((Connection c) -> c.getMetaData().getDatabaseProductName().toLowerCase().contains("postgres"));
        return Boolean.TRUE.equals(pg);
    }
}
