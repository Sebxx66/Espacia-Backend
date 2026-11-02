package com.bibliospace.backend;

import com.bibliospace.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RulesApiTests extends ApiTestBase {

    private static Map<String, Object> reglas(int maxHoras) {
        Map<String, Object> r = new HashMap<>();
        r.put("horaApertura", 8);
        r.put("horaCierre", 20);
        r.put("maxHorasReserva", maxHoras);
        r.put("maxReservasActivas", 3);
        r.put("maxEquiposDia", 1);
        r.put("maxSalasDia", 2);
        r.put("horasCancelacion", 1);
        r.put("minutosNoAsistencia", 30);
        r.put("diasAnticipacion", 30);
        return r;
    }

    @Test
    void lasReglasVigentesSonPublicas() {
        Response r = get("/rules", null);

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("horaApertura").asInt()).isEqualTo(8);
        assertThat(r.body().has("actualizadoPor")).isFalse();
    }

    @Test
    void elAdministradorCambiaLasReglasYSeAplicanDeInmediato() {
        String admin = adminToken();
        try {
            assertThat(call("PUT", "/rules", reglas(2), admin).status()).isEqualTo(200);

            Response larga = reserve(newStudent(), newSpace(), tomorrow(), "10:00", "13:00");
            assertThat(larga.status()).isEqualTo(400);
            assertThat(larga.message()).contains("2 horas");
            assertThat(get("/rules", null).body().get("maxHorasReserva").asInt()).isEqualTo(2);
        } finally {
            // Las demás pruebas usan los valores por defecto
            call("PUT", "/rules", reglas(4), admin);
        }
    }

    @Test
    void reglasIncoherentesSeRechazan() {
        String admin = adminToken();
        Map<String, Object> cierreAntes = reglas(4);
        cierreAntes.put("horaCierre", 7);
        Map<String, Object> masHorasQueApertura = reglas(4);
        masHorasQueApertura.put("horaApertura", 18);

        assertThat(call("PUT", "/rules", cierreAntes, admin).status()).isEqualTo(400);
        assertThat(call("PUT", "/rules", masHorasQueApertura, admin).message()).contains("horas de apertura");
        assertThat(get("/rules", null).body().get("horaApertura").asInt()).isEqualTo(8);
    }

    @Test
    void soloElAdministradorPuedeCambiarLasReglas() {
        assertThat(call("PUT", "/rules", reglas(4), newStudent()).status()).isEqualTo(403);
        assertThat(call("PUT", "/rules", reglas(4), newUserWithRole("ROLE_FUNCIONARIO")).status()).isEqualTo(403);
    }
}
