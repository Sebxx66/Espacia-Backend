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

    @Test
    void losDominiosInstitucionalesIdentificanALosUsuarios() {
        assertThat(get("/rules", null).body().get("dominiosInstitucionales").asString()).isEqualTo("amigo.edu.co");

        assertThat(register("ana-" + System.nanoTime() + "@amigo.edu.co", PASSWORD).body().get("institucional").asBoolean()).isTrue();
        assertThat(register("ana-" + System.nanoTime() + "@est.amigo.edu.co", PASSWORD).body().get("institucional").asBoolean()).isTrue();
        assertThat(register("ana-" + System.nanoTime() + "@gmail.com", PASSWORD).body().get("institucional").asBoolean()).isFalse();
        assertThat(register("ana-" + System.nanoTime() + "@falsoamigo.edu.co", PASSWORD).body().get("institucional").asBoolean()).isFalse();
    }

    @Test
    void elAdministradorCambiaLosDominiosInstitucionales() {
        String admin = adminToken();
        try {
            Map<String, Object> conDominios = reglas(4);
            conDominios.put("dominiosInstitucionales", " @Uni.edu.co, otra.edu.co\nuni.edu.co ");
            Response r = call("PUT", "/rules", conDominios, admin);
            assertThat(r.status()).isEqualTo(200);
            assertThat(r.body().get("dominiosInstitucionales").asString()).isEqualTo("uni.edu.co,otra.edu.co");

            // Si no se envían, se conservan
            assertThat(call("PUT", "/rules", reglas(4), admin).body().get("dominiosInstitucionales").asString())
                    .isEqualTo("uni.edu.co,otra.edu.co");

            Map<String, Object> invalido = reglas(4);
            invalido.put("dominiosInstitucionales", "amigo.edu.co, no es dominio");
            Response mal = call("PUT", "/rules", invalido, admin);
            assertThat(mal.status()).isEqualTo(400);
            assertThat(mal.message()).contains("no es un dominio válido");
        } finally {
            Map<String, Object> original = reglas(4);
            original.put("dominiosInstitucionales", "amigo.edu.co");
            call("PUT", "/rules", original, admin);
        }
    }
}
