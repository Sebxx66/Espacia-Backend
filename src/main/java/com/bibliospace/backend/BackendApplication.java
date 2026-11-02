package com.bibliospace.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.ZoneId;
import java.util.TimeZone;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		// Todas las reglas de horario (horas pasadas, recordatorios, no asistidas) usan la hora local de la
		// biblioteca, aunque el servidor esté en otra zona (por ejemplo, UTC en la nube). Se fija antes de
		// arrancar cualquier componente, incluida la conexión a la base de datos.
		String zona = System.getenv().getOrDefault("APP_TIME_ZONE", "America/Bogota");
		TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(zona)));
		SpringApplication.run(BackendApplication.class, args);
	}

}
