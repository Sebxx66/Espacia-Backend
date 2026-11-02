package com.bibliospace.backend;

import com.bibliospace.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthApiTests extends ApiTestBase {

    @Test
    void registroCreaUnEstudianteQueDebeConfirmarSuCorreo() {
        String email = uniqueEmail("nuevo");
        Response r = register(email, PASSWORD);

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("verificacionPendiente").asBoolean()).isTrue();
        assertThat(r.body().has("token")).isFalse();

        Response login = post("/auth/login", Map.of("email", email, "password", PASSWORD), null);
        assertThat(login.status()).isEqualTo(403);
        assertThat(login.body().get("code").asString()).isEqualTo("EMAIL_NO_VERIFICADO");

        Response confirmado = registerVerifiedExisting(email);
        assertThat(confirmado.body().get("rol").asString()).isEqualTo("ROLE_ESTUDIANTE");
    }

    // La contraseña equivocada no revela que la cuenta está pendiente
    @Test
    void cuentaSinConfirmarConContrasenaIncorrectaResponde401() {
        String email = uniqueEmail("pendiente");
        register(email, PASSWORD);

        assertThat(post("/auth/login", Map.of("email", email, "password", "otra-clave-123"), null).status()).isEqualTo(401);
    }

    private Response registerVerifiedExisting(String email) {
        var user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.marcarCorreoVerificado();
        userRepository.save(user);
        return post("/auth/login", Map.of("email", email, "password", PASSWORD), null);
    }

    @Test
    void registroExigeAutorizarElTratamientoDeDatos() {
        String email = uniqueEmail("sin-autorizacion");
        Response sin = post("/auth/register", Map.of("nombre", "Ana", "email", email, "password", PASSWORD), null);
        Response rechazada = post("/auth/register",
                Map.of("nombre", "Ana", "email", email, "password", PASSWORD, "aceptaPrivacidad", false), null);

        assertThat(sin.status()).isEqualTo(400);
        assertThat(sin.message()).contains("tratamiento de tus datos");
        assertThat(rechazada.status()).isEqualTo(400);
        assertThat(post("/auth/login", Map.of("email", email, "password", PASSWORD), null).status()).isEqualTo(401);
    }

    @Test
    void registroRechazaContrasenasDeMenosDe8Caracteres() {
        Response r = post("/auth/register", Map.of("nombre", "Ana", "email", uniqueEmail("corta"), "password", "1234567", "aceptaPrivacidad", true), null);

        assertThat(r.status()).isEqualTo(400);
        assertThat(r.message()).contains("8 caracteres");
    }

    @Test
    void registroRechazaEmailDuplicadoSinImportarMayusculas() {
        String email = uniqueEmail("dup");
        registerVerified(email);

        Response r = post("/auth/register", Map.of("nombre", "Otra", "email", email.toUpperCase(), "password", PASSWORD, "aceptaPrivacidad", true), null);

        assertThat(r.status()).isEqualTo(409);
    }

    @Test
    void loginConContrasenaIncorrectaResponde401ConMensaje() {
        String email = uniqueEmail("login");
        registerVerified(email);

        Response r = post("/auth/login", Map.of("email", email, "password", "otra-clave-123"), null);

        assertThat(r.status()).isEqualTo(401);
        assertThat(r.message()).isEqualTo("Email o contraseña incorrectos.");
    }

    @Test
    void tras5ContrasenasIncorrectasLaCuentaSeBloqueaTemporalmente() {
        String email = uniqueEmail("bloqueo");
        registerVerified(email);
        for (int i = 0; i < 5; i++) {
            assertThat(post("/auth/login", Map.of("email", email, "password", "incorrecta-" + i), null).status()).isEqualTo(401);
        }

        Response bloqueado = post("/auth/login", Map.of("email", email, "password", PASSWORD), null);

        assertThat(bloqueado.status()).isEqualTo(429);
        assertThat(bloqueado.message()).contains("Demasiados intentos");
    }

    @Test
    void sinTokenLaApiResponde401() {
        assertThat(get("/spaces", null).status()).isEqualTo(401);
        assertThat(get("/spaces", "token-inventado").status()).isEqualTo(401);
    }

    @Test
    void cambioDeContrasenaExigeLaActualCorrecta() {
        String email = uniqueEmail("pwd");
        String token = registerVerified(email).body().get("token").asString();

        Response mala = patch("/auth/password", Map.of("actual", "no-es-esta", "nueva", "nueva-clave-9"), token);
        Response buena = patch("/auth/password", Map.of("actual", PASSWORD, "nueva", "nueva-clave-9"), token);

        assertThat(mala.status()).isEqualTo(400);
        assertThat(buena.status()).isEqualTo(200);
        assertThat(buena.body().get("token").asString()).isNotBlank();
        assertThat(post("/auth/login", Map.of("email", email, "password", "nueva-clave-9"), null).status()).isEqualTo(200);
    }

    @Test
    void cambiarLaContrasenaCierraLasDemasSesiones() throws InterruptedException {
        String email = uniqueEmail("sesiones");
        String celular = registerVerified(email).body().get("token").asString();
        String computador = login(email, PASSWORD);
        // Los tokens guardan la hora de emisión en segundos: se espera para que el cambio sea posterior
        Thread.sleep(1100);

        Response cambio = patch("/auth/password", Map.of("actual", PASSWORD, "nueva", "nueva-clave-9"), computador);
        String nuevo = cambio.body().get("token").asString();

        assertThat(get("/auth/me", celular).status()).isEqualTo(401);
        assertThat(get("/auth/me", computador).status()).isEqualTo(401);
        assertThat(get("/auth/me", nuevo).status()).isEqualTo(200);
    }

    @Test
    void restablecerLaContrasenaCierraLasSesionesDelUsuario() throws InterruptedException {
        String email = uniqueEmail("reset-sesion");
        Response registro = registerVerified(email);
        String token = registro.body().get("token").asString();
        long id = registro.body().get("id").asLong();
        Thread.sleep(1100);

        assertThat(patch("/users/" + id + "/password", Map.of("password", "temporal-123"), adminToken()).status()).isEqualTo(204);

        assertThat(get("/auth/me", token).status()).isEqualTo(401);
        assertThat(get("/auth/me", login(email, "temporal-123")).status()).isEqualTo(200);
    }

    @Test
    void cuentaSuspendidaNoPuedeEntrarYSuTokenDejaDeServir() {
        String email = uniqueEmail("susp");
        Response registro = registerVerified(email);
        String token = registro.body().get("token").asString();
        long id = registro.body().get("id").asLong();

        patch("/users/" + id, Map.of("activo", false), adminToken());

        Response login = post("/auth/login", Map.of("email", email, "password", PASSWORD), null);
        assertThat(login.status()).isEqualTo(403);
        assertThat(login.message()).contains("suspendida");
        assertThat(get("/spaces", token).status()).isEqualTo(401);
    }

    @Test
    void administradorRestableceLaContrasenaDeUnUsuario() {
        String email = uniqueEmail("reset");
        long id = registerVerified(email).body().get("id").asLong();

        Response r = patch("/users/" + id + "/password", Map.of("password", "temporal-123"), adminToken());

        assertThat(r.status()).isEqualTo(204);
        assertThat(post("/auth/login", Map.of("email", email, "password", PASSWORD), null).status()).isEqualTo(401);
        assertThat(post("/auth/login", Map.of("email", email, "password", "temporal-123"), null).status()).isEqualTo(200);
    }
}
