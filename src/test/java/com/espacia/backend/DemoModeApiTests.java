package com.espacia.backend;

import com.espacia.backend.demo.DemoDataService;
import com.espacia.backend.entities.Role;
import com.espacia.backend.repositories.ReservationRepository;
import com.espacia.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Demostración pública: cuentas visibles en la pantalla de inicio, permisos recortados y reinicio nocturno
@TestPropertySource(properties = {"app.demo.enabled=true", "app.seed.demo-password=Demo-pruebas-77"})
class DemoModeApiTests extends ApiTestBase {

    private static final String CLAVE = "Demo-pruebas-77";

    @Autowired
    private DemoDataService demoDataService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void publicaLasCuentasDeDemostracionSinIniciarSesion() {
        Response r = get("/demo", null);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("enabled").asBoolean()).isTrue();
        assertThat(r.body().get("accounts")).hasSize(3);
        assertThat(r.body().get("accounts").get(2).get("rol").asString()).isEqualTo("ROLE_ADMIN");
        assertThat(r.body().get("accounts").get(0).get("password").asString()).isEqualTo(CLAVE);
    }

    @Test
    void lasCuentasDeDemostracionPuedenEntrarYTienenDatos() {
        String token = login("estudiante@espacia.local", CLAVE);
        Response reservas = get("/reservations/me", token);
        assertThat(reservas.status()).isEqualTo(200);
        assertThat(reservas.body().size()).isGreaterThan(0);
    }

    @Test
    void elAdministradorDeDemostracionSoloConsulta() {
        String token = login(DemoDataService.ADMIN_DEMO.email(), CLAVE);
        assertThat(get("/users", token).status()).isEqualTo(200);
        assertThat(get("/admin/dashboard", token).status()).isEqualTo(200);

        Response crear = post("/spaces", Map.of("nombre", "No debería existir", "tipo", "SALA_ESTUDIO",
                "capacidad", 2, "estado", "ACTIVO"), token);
        assertThat(crear.status()).isEqualTo(403);
        assertThat(crear.message()).contains("solo puede consultar");
    }

    @Test
    void lasCuentasDeDemostracionNoCambianSuContrasena() {
        String token = login("funcionario@espacia.local", CLAVE);
        Response r = patch("/auth/password", Map.of("actual", CLAVE, "nueva", "otra-clave-larga-123"), token);
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.message()).contains("no pueden cambiar la contraseña");
    }

    @Test
    void lasCuentasNormalesNoTienenRestricciones() {
        String admin = adminToken();
        Response crear = post("/spaces", Map.of("nombre", "Sala real", "tipo", "SALA_ESTUDIO",
                "capacidad", 2, "estado", "ACTIVO"), admin);
        assertThat(crear.status()).isEqualTo(200);
    }

    @Test
    void elReinicioBorraLoQueHicieronLosVisitantes() {
        String token = login("estudiante@espacia.local", CLAVE);
        long espacio = newSpace();
        Response reserva = reserve(token, espacio, tomorrow(), "15:00", "16:00");
        assertThat(reserva.status()).as("reserva: %s", reserva.body()).isEqualTo(200);
        long id = reserva.body().get("id").asLong();

        // Alguien desactiva una cuenta de demostración desde una cuenta real
        var funcionario = userRepository.findByEmailIgnoreCase("funcionario@espacia.local").orElseThrow();
        funcionario.setActivo(false);
        userRepository.save(funcionario);

        demoDataService.reiniciar();

        assertThat(reservationRepository.findById(id)).isEmpty();
        var restaurado = userRepository.findByEmailIgnoreCase("funcionario@espacia.local").orElseThrow();
        assertThat(restaurado.getActivo()).isTrue();
        assertThat(restaurado.getRol()).isEqualTo(Role.ROLE_FUNCIONARIO);
        assertThat(get("/reservations/me", token).body().size()).isGreaterThan(0);
    }
}
