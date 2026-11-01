package com.bibliospace.backend;

import com.bibliospace.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ReservationApiTests extends ApiTestBase {

    @Test
    void reservaValidaQuedaConfirmadaYApareceEnMisReservas() {
        String token = newStudent();
        long espacio = newSpace();

        Response r = reserve(token, espacio, tomorrow(), "10:00", "12:00");

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("estado").asString()).isEqualTo("CONFIRMADA");
        assertThat(r.body().get("codigo").asString()).matches("R-\\d{4}-\\d{4}");
        assertThat(get("/reservations/me", token).body().size()).isEqualTo(1);
    }

    @Test
    void rechazaReservasDeMasDe4Horas() {
        Response r = reserve(newStudent(), newSpace(), tomorrow(), "10:00", "15:00");

        assertThat(r.status()).isEqualTo(400);
        assertThat(r.message()).contains("4 horas");
    }

    @Test
    void rechazaHorariosFueraDe8A20() {
        String token = newStudent();
        long espacio = newSpace();

        assertThat(reserve(token, espacio, tomorrow(), "07:00", "09:00").status()).isEqualTo(400);
        assertThat(reserve(token, espacio, tomorrow(), "19:00", "21:00").status()).isEqualTo(400);
    }

    @Test
    void rechazaFechasPasadasYDeMasDe30Dias() {
        String token = newStudent();
        long espacio = newSpace();

        assertThat(reserve(token, espacio, LocalDate.now().minusDays(1), "10:00", "11:00").status()).isEqualTo(400);
        assertThat(reserve(token, espacio, LocalDate.now().plusDays(31), "10:00", "11:00").status()).isEqualTo(400);
    }

    @Test
    void rechazaHorasDeHoyQueYaPasaron() {
        // Reserva la hora en curso (por ejemplo 15:00 si son las 15:40): ya empezó, así que se rechaza.
        // Solo aplica dentro del horario de la biblioteca (8:00 a 20:00).
        LocalTime ahora = LocalTime.now();
        assumeTrue(ahora.getHour() >= 8 && ahora.getHour() < 20);
        String token = newStudent();
        long espacio = newSpace();

        Response r = reserve(token, espacio, LocalDate.now(),
                String.format("%02d:00", ahora.getHour()), String.format("%02d:00", ahora.getHour() + 1));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.body().get("message").asString()).contains("ya pasó");
    }

    @Test
    void noPermiteReservasSuperpuestasEnElMismoEspacio() {
        long espacio = newSpace();
        reserve(newStudent(), espacio, tomorrow(), "10:00", "12:00");

        Response choque = reserve(newStudent(), espacio, tomorrow(), "11:00", "13:00");
        Response contigua = reserve(newStudent(), espacio, tomorrow(), "12:00", "13:00");

        assertThat(choque.status()).isEqualTo(409);
        assertThat(contigua.status()).isEqualTo(200);
    }

    @Test
    void unUsuarioNoPuedeTenerDosReservasALaMismaHora() {
        String token = newStudent();
        reserve(token, newSpace(), tomorrow(), "10:00", "12:00");

        assertThat(reserve(token, newSpace(), tomorrow(), "11:00", "12:00").status()).isEqualTo(409);
    }

    @Test
    void maximo3ReservasActivasPorUsuario() {
        String token = newStudent();
        // En días distintos para no chocar con el límite diario
        reserve(token, newSpace(), LocalDate.now().plusDays(1), "08:00", "09:00");
        reserve(token, newSpace(), LocalDate.now().plusDays(2), "10:00", "11:00");
        reserve(token, newSpace(), LocalDate.now().plusDays(3), "12:00", "13:00");

        Response cuarta = reserve(token, newSpace(), LocalDate.now().plusDays(4), "14:00", "15:00");

        assertThat(cuarta.status()).isEqualTo(400);
        assertThat(cuarta.message()).contains("3 reservas activas");
    }

    @Test
    void maximo2SalasPorDia() {
        String token = newStudent();
        assertThat(reserve(token, newSpace(), tomorrow(), "08:00", "09:00").status()).isEqualTo(200);
        assertThat(reserve(token, newSpace(), tomorrow(), "10:00", "11:00").status()).isEqualTo(200);

        Response tercera = reserve(token, newSpace(), tomorrow(), "12:00", "13:00");

        assertThat(tercera.status()).isEqualTo(400);
        assertThat(tercera.message()).contains("2 salas");
        // Otro día sí se puede
        assertThat(reserve(token, newSpace(), LocalDate.now().plusDays(2), "12:00", "13:00").status()).isEqualTo(200);
    }

    @Test
    void maximo1EquipoPorDia() {
        String token = newStudent();
        assertThat(reserve(token, newSpace("EQUIPO_TECNOLOGICO"), tomorrow(), "08:00", "09:00").status()).isEqualTo(200);

        Response segundo = reserve(token, newSpace("EQUIPO_TECNOLOGICO"), tomorrow(), "10:00", "11:00");

        assertThat(segundo.status()).isEqualTo(400);
        assertThat(segundo.message()).contains("1 equipo");
        // Una sala el mismo día no cuenta como equipo
        assertThat(reserve(token, newSpace(), tomorrow(), "12:00", "13:00").status()).isEqualTo(200);
    }

    @Test
    void cancelarUnaReservaLiberaElCupoDiario() {
        String token = newStudent();
        long id = reserve(token, newSpace("EQUIPO_TECNOLOGICO"), tomorrow(), "08:00", "09:00").body().get("id").asLong();
        assertThat(reserve(token, newSpace("EQUIPO_TECNOLOGICO"), tomorrow(), "10:00", "11:00").status()).isEqualTo(400);

        assertThat(patch("/reservations/" + id + "/cancel", null, token).status()).isEqualTo(200);

        assertThat(reserve(token, newSpace("EQUIPO_TECNOLOGICO"), tomorrow(), "10:00", "11:00").status()).isEqualTo(200);
    }

    @Test
    void cancelarLiberaElHorarioYElCupo() {
        String token = newStudent();
        long espacio = newSpace();
        long id = reserve(token, espacio, tomorrow(), "10:00", "12:00").body().get("id").asLong();

        Response r = patch("/reservations/" + id + "/cancel", null, token);

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("estado").asString()).isEqualTo("CANCELADA");
        assertThat(reserve(newStudent(), espacio, tomorrow(), "10:00", "12:00").status()).isEqualTo(200);
    }

    @Test
    void nadiePuedeCancelarLaReservaDeOtro() {
        long id = reserve(newStudent(), newSpace(), tomorrow(), "10:00", "11:00").body().get("id").asLong();

        assertThat(patch("/reservations/" + id + "/cancel", null, newStudent()).status()).isEqualTo(404);
    }

    @Test
    void extenderSumaUnaHoraSinPasarDe4NiChocar() {
        String token = newStudent();
        long espacio = newSpace();
        long id = reserve(token, espacio, tomorrow(), "10:00", "13:00").body().get("id").asLong();

        Response extendida = patch("/reservations/" + id + "/extend", null, token);
        Response otraVez = patch("/reservations/" + id + "/extend", null, token);

        assertThat(extendida.status()).isEqualTo(200);
        assertThat(extendida.body().get("horaFin").asString()).startsWith("14:00");
        assertThat(otraVez.status()).as("ya dura 4 horas").isEqualTo(400);

        long otra = reserve(token, espacio, tomorrow(), "16:00", "17:00").body().get("id").asLong();
        reserve(newStudent(), espacio, tomorrow(), "17:00", "18:00");
        assertThat(patch("/reservations/" + otra + "/extend", null, token).status()).as("choca con la siguiente").isEqualTo(409);
    }

    @Test
    void espacioEnMantenimientoNoSeReservaYAnulaLasReservasFuturas() {
        String estudiante = newStudent();
        long espacio = newSpace();
        long id = reserve(estudiante, espacio, tomorrow(), "10:00", "11:00").body().get("id").asLong();

        Response incidencia = post("/incidents",
                Map.of("espacioId", espacio, "descripcion", "Proyector dañado", "ponerEnMantenimiento", true),
                newUserWithRole("ROLE_FUNCIONARIO"));

        assertThat(incidencia.status()).isEqualTo(200);
        assertThat(reserve(newStudent(), espacio, tomorrow(), "12:00", "13:00").status()).isEqualTo(400);
        var mias = get("/reservations/me", estudiante).body();
        assertThat(mias.get(0).get("id").asLong()).isEqualTo(id);
        assertThat(mias.get(0).get("estado").asString()).isEqualTo("RECHAZADA");
        assertThat(get("/notifications/me", estudiante).body().get(0).get("mensaje").asString()).contains("mantenimiento");
    }

    @Test
    void funcionarioValidaLlegadaYFinalizaElUso() {
        int ahora = LocalTime.now().getHour();
        assumeTrue(ahora >= 7 && ahora <= 18, "Necesita un bloque libre hoy entre las 08:00 y las 20:00");
        String inicio = String.format("%02d:00", ahora + 1);
        String fin = String.format("%02d:00", ahora + 2);

        String estudiante = newStudent();
        String funcionario = newUserWithRole("ROLE_FUNCIONARIO");
        long id = reserve(estudiante, newSpace(), LocalDate.now(), inicio, fin).body().get("id").asLong();

        assertThat(patch("/reservations/" + id + "/check-in", null, estudiante).status()).isEqualTo(403);
        Response checkIn = patch("/reservations/" + id + "/check-in", null, funcionario);
        Response fin2 = patch("/reservations/" + id + "/finish", null, funcionario);

        assertThat(checkIn.body().get("estado").asString()).isEqualTo("EN_USO");
        assertThat(fin2.body().get("estado").asString()).isEqualTo("FINALIZADA");
    }

    @Test
    void solosePuedeValidarLaLlegadaElDiaDeLaReserva() {
        long id = reserve(newStudent(), newSpace(), tomorrow(), "10:00", "11:00").body().get("id").asLong();

        Response r = patch("/reservations/" + id + "/check-in", null, newUserWithRole("ROLE_FUNCIONARIO"));

        assertThat(r.status()).isEqualTo(400);
    }
}
