package com.bibliospace.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;
import java.util.TimeZone;

// Todas las reglas de horario (horas ya pasadas, recordatorios, no asistidas) usan la hora local de la
// biblioteca, aunque el servidor esté en otra zona (por ejemplo, UTC en un servidor en la nube).
@Slf4j
@Configuration
public class TimeZoneConfig {

    public TimeZoneConfig(@Value("${app.time-zone:America/Bogota}") String zona) {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(zona)));
        log.info("Zona horaria de la aplicación: {}", zona);
    }
}
