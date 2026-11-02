package com.bibliospace.backend;

import com.bibliospace.backend.entities.Reservation;
import com.bibliospace.backend.repositories.ReservationRepository;
import com.bibliospace.backend.services.NoShowService;
import com.bibliospace.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NoShowApiTests extends ApiTestBase {

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private NoShowService noShowService;

    // Crea una reserva por la API (para mañana) y la mueve a hoy, empezando hace `minutos`
    private long reservaQueEmpezoHace(String token, int minutos) {
        long id = reserve(token, newSpace(), tomorrow(), "10:00", "11:00").body().get("id").asLong();
        Reservation r = reservationRepository.findById(id).orElseThrow();
        LocalTime inicio = LocalTime.now().minusMinutes(minutos).truncatedTo(ChronoUnit.MINUTES);
        r.setFecha(LocalDate.now());
        r.setHoraInicio(inicio);
        r.setHoraFin(inicio.plusHours(1));
        reservationRepository.save(r);
        return id;
    }

    private String estado(long id) {
        return reservationRepository.findById(id).orElseThrow().getEstado().name();
    }

    @Test
    void sinLlegadaALos30MinutosLaReservaSeLibera() {
        // Evita el cambio de día al restar minutos justo después de medianoche
        assumeTrue(LocalTime.now().isAfter(LocalTime.of(1, 0)) && LocalTime.now().isBefore(LocalTime.of(22, 0)));
        String token = newStudent();
        long vencida = reservaQueEmpezoHace(token, 31);
        long reciente = reservaQueEmpezoHace(newStudent(), 10);

        noShowService.liberarNoAsistidas();

        assertThat(estado(vencida)).isEqualTo("NO_ASISTIDA");
        assertThat(estado(reciente)).isEqualTo("CONFIRMADA");
        assertThat(get("/notifications/me", token).body().toString()).contains("no asistida");
    }

    @Test
    void elPersonalSoloMarcaNoAsistidaCuandoLaReservaYaEmpezo() {
        assumeTrue(LocalTime.now().isAfter(LocalTime.of(1, 0)) && LocalTime.now().isBefore(LocalTime.of(22, 0)));
        String funcionario = newUserWithRole("ROLE_FUNCIONARIO");
        long futura = reserve(newStudent(), newSpace(), tomorrow(), "10:00", "11:00").body().get("id").asLong();
        long empezada = reservaQueEmpezoHace(newStudent(), 5);

        assertThat(patch("/reservations/" + futura + "/no-show", null, funcionario).status()).isEqualTo(400);
        Response r = patch("/reservations/" + empezada + "/no-show", null, funcionario);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("estado").asString()).isEqualTo("NO_ASISTIDA");
    }

    @Test
    void laDocumentacionDeLaApiEsPublica() {
        Response docs = getRaw("/v3/api-docs");
        assertThat(docs.status()).isEqualTo(200);
        assertThat(docs.body().path("info").path("title").asString()).isEqualTo("Espacia API");
    }
}
