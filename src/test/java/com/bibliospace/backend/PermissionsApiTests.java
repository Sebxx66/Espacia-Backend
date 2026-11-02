package com.bibliospace.backend;

import com.bibliospace.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Los permisos por rol se validan en el backend, no solo ocultando botones en el frontend
class PermissionsApiTests extends ApiTestBase {

    private static final Map<String, Object> ESPACIO = Map.of("nombre", "X", "tipo", "TALLER", "capacidad", 1, "estado", "ACTIVO");

    @Test
    void estudianteNoAccedeAFuncionesDelPersonal() {
        String estudiante = newStudent();

        assertThat(post("/spaces", ESPACIO, estudiante).status()).isEqualTo(403);
        assertThat(get("/users", estudiante).status()).isEqualTo(403);
        assertThat(get("/reservations", estudiante).status()).isEqualTo(403);
        assertThat(get("/incidents", estudiante).status()).isEqualTo(403);
        assertThat(get("/admin/dashboard", estudiante).status()).isEqualTo(403);
    }

    @Test
    void funcionarioGestionaReservasEIncidenciasPeroNoEspaciosNiUsuarios() {
        String funcionario = newUserWithRole("ROLE_FUNCIONARIO");

        assertThat(get("/reservations", funcionario).status()).isEqualTo(200);
        assertThat(get("/incidents", funcionario).status()).isEqualTo(200);
        assertThat(post("/spaces", ESPACIO, funcionario).status()).isEqualTo(403);
        assertThat(get("/users", funcionario).status()).isEqualTo(403);
        assertThat(get("/admin/dashboard", funcionario).status()).isEqualTo(403);
    }

    @Test
    void administradorAccedeATodo() {
        String admin = adminToken();

        assertThat(get("/users", admin).status()).isEqualTo(200);
        assertThat(get("/admin/dashboard", admin).status()).isEqualTo(200);
        assertThat(get("/reservations", admin).status()).isEqualTo(200);
        assertThat(get("/incidents", admin).status()).isEqualTo(200);
    }

    @Test
    void elRegistroPublicoNuncaCreaAdministradores() {
        Response r = post("/auth/register",
                Map.of("nombre", "Pícaro", "email", uniqueEmail("picaro"), "password", PASSWORD, "rol", "ROLE_ADMIN", "aceptaPrivacidad", true), null);

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("rol").asString()).isEqualTo("ROLE_ESTUDIANTE");
    }

    @Test
    void administradorNoPuedeCambiarseElRolNiSuspenderseASiMismo() {
        String admin = adminToken();
        long miId = get("/auth/me", admin).body().get("id").asLong();

        assertThat(patch("/users/" + miId, Map.of("rol", "ROLE_ESTUDIANTE"), admin).status()).isEqualTo(400);
        assertThat(patch("/users/" + miId, Map.of("activo", false), admin).status()).isEqualTo(400);
    }
}
