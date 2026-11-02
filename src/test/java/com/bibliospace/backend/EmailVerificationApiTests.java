package com.bibliospace.backend;

import com.bibliospace.backend.services.EmailService;
import com.bibliospace.backend.support.ApiTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

// El servicio de correo se reemplaza por uno simulado para leer el enlace que se habría enviado.
// Usa su propia base en memoria porque levanta un contexto distinto al de las demás pruebas.
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:verificacion;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class EmailVerificationApiTests extends ApiTestBase {

    @MockitoBean
    private EmailService emailService;

    @BeforeEach
    void limpiarSimulacion() {
        Mockito.clearInvocations(emailService);
    }

    private Response registrar(String email, String password, String appUrl) {
        Map<String, Object> body = new HashMap<>(Map.of("nombre", "Ana", "email", email, "password", password, "aceptaPrivacidad", true));
        if (appUrl != null) body.put("appUrl", appUrl);
        return post("/auth/register", body, null);
    }

    // Último enlace de verificación "enviado" a ese correo
    private String enlace(String email) {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService, Mockito.atLeastOnce()).sendVerification(eq(email), anyString(), link.capture(), anyInt());
        return link.getValue();
    }

    private static String token(String link) {
        return link.substring(link.indexOf("?verificar=") + "?verificar=".length());
    }

    @Test
    void elEnlaceConLaContrasenaDelRegistroActivaLaCuentaEIniciaSesion() {
        String email = uniqueEmail("verifica");
        assertThat(registrar(email, PASSWORD, null).status()).isEqualTo(200);
        String token = token(enlace(email));

        Response revisado = post("/auth/verify-email/check", Map.of("token", token), null);
        assertThat(revisado.status()).isEqualTo(200);
        assertThat(revisado.body().get("email").asString()).isEqualTo(email);

        Response mala = post("/auth/verify-email", Map.of("token", token, "password", "no-es-esta-1"), null);
        assertThat(mala.status()).isEqualTo(400);
        assertThat(mala.message()).contains("no coincide");

        Response ok = post("/auth/verify-email", Map.of("token", token, "password", PASSWORD), null);
        assertThat(ok.status()).isEqualTo(200);
        assertThat(get("/auth/me", ok.body().get("token").asString()).status()).isEqualTo(200);
        verify(emailService).sendWelcome(eq(email), anyString());

        // Ya puede entrar normalmente y el enlace no se puede volver a usar
        assertThat(post("/auth/login", Map.of("email", email, "password", PASSWORD), null).status()).isEqualTo(200);
        assertThat(post("/auth/verify-email", Map.of("token", token, "password", PASSWORD), null).status()).isEqualTo(400);
        assertThat(registrar(email, PASSWORD, null).status()).isEqualTo(409);
    }

    @Test
    void elEnlaceApuntaALaDireccionDeLaRedLocalPeroNuncaAUnSitioExterno() {
        String local = uniqueEmail("red-local");
        registrar(local, PASSWORD, "http://192.168.1.50:8443");
        assertThat(enlace(local)).startsWith("http://192.168.1.50:8443/?verificar=");

        String externo = uniqueEmail("externo");
        registrar(externo, PASSWORD, "https://sitio-falso.example");
        assertThat(enlace(externo)).startsWith("http://localhost:8443/?verificar=");

        String raro = uniqueEmail("raro");
        registrar(raro, PASSWORD, "http://192.168.1.50:8443/otra/ruta?x=1");
        assertThat(enlace(raro)).startsWith("http://localhost:8443/?verificar=");
    }

    @Test
    void registrarseDeNuevoConUnCorreoSinConfirmarReemplazaLaContrasenaYElEnlace() {
        String email = uniqueEmail("de-nuevo");
        registrar(email, PASSWORD, null);
        String primero = token(enlace(email));
        Mockito.clearInvocations(emailService);

        assertThat(registrar(email, "otra-clave-22", null).status()).isEqualTo(200);
        String segundo = token(enlace(email));

        assertThat(post("/auth/verify-email", Map.of("token", primero, "password", PASSWORD), null).status()).isEqualTo(400);
        assertThat(post("/auth/verify-email", Map.of("token", segundo, "password", PASSWORD), null).status()).isEqualTo(400);
        assertThat(post("/auth/verify-email", Map.of("token", segundo, "password", "otra-clave-22"), null).status()).isEqualTo(200);
    }

    @Test
    void reenviarElEnlaceEnviaUnoNuevoYNoRevelaSiLaCuentaExiste() {
        String email = uniqueEmail("reenvio");
        registrar(email, PASSWORD, null);
        String primero = token(enlace(email));
        Mockito.clearInvocations(emailService);

        assertThat(post("/auth/resend-verification", Map.of("email", email), null).status()).isEqualTo(204);
        String nuevo = token(enlace(email));
        assertThat(nuevo).isNotEqualTo(primero);
        assertThat(post("/auth/verify-email/check", Map.of("token", primero), null).status()).isEqualTo(400);

        assertThat(post("/auth/resend-verification", Map.of("email", uniqueEmail("no-existe")), null).status()).isEqualTo(204);
    }

    @Test
    void demasiadosEnlacesSeguidosSeLimitan() {
        String email = uniqueEmail("limite");
        assertThat(registrar(email, PASSWORD, null).status()).isEqualTo(200);
        assertThat(registrar(email, PASSWORD, null).status()).isEqualTo(200);
        assertThat(registrar(email, PASSWORD, null).status()).isEqualTo(200);

        Response cuarto = registrar(email, PASSWORD, null);
        assertThat(cuarto.status()).isEqualTo(429);
    }

    @Test
    void elAdministradorPuedeConfirmarElCorreoAMano() {
        String email = uniqueEmail("manual");
        registrar(email, PASSWORD, null);
        String admin = adminToken();
        long id = 0;
        for (var u : get("/users", admin).body()) {
            if (u.get("email").asString().equals(email)) {
                assertThat(u.get("emailVerificado").asBoolean()).isFalse();
                id = u.get("id").asLong();
            }
        }

        Response r = patch("/users/" + id + "/verify-email", null, admin);

        assertThat(r.status()).isEqualTo(200);
        assertThat(r.body().get("emailVerificado").asBoolean()).isTrue();
        assertThat(post("/auth/login", Map.of("email", email, "password", PASSWORD), null).status()).isEqualTo(200);
    }

    @Test
    void lasCuentasQueCreaElAdministradorNoNecesitanConfirmar() {
        String email = uniqueEmail("creada");
        Response creada = post("/users", Map.of("nombre", "Func", "email", email, "password", PASSWORD, "rol", "ROLE_FUNCIONARIO"), adminToken());

        assertThat(creada.body().get("emailVerificado").asBoolean()).isTrue();
        assertThat(post("/auth/login", Map.of("email", email, "password", PASSWORD), null).status()).isEqualTo(200);
        verify(emailService, Mockito.never()).sendVerification(eq(email), anyString(), anyString(), anyInt());
    }
}
