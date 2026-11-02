package com.bibliospace.backend;

import com.bibliospace.backend.services.EmailService;
import com.bibliospace.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// Topes por red (IP) con valores bajos. Cada prueba usa IPs propias, así no se afectan entre sí.
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:limitesip;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "app.limits.registros-por-ip-hora=2",
        "app.limits.registros-por-hora=1000",
        "app.limits.correos-por-ip-hora=1",
        "app.limits.fallos-login-por-ip=3"})
class IpRateLimitApiTests extends ApiTestBase {

    @MockitoBean
    private EmailService emailService;

    private Response registrarDesde(String ip, String email) {
        return callFrom(ip, "POST", "/auth/register",
                Map.of("nombre", "Ana", "email", email, "password", PASSWORD, "aceptaPrivacidad", true), null);
    }

    @Test
    void unaRedNoPuedeCrearCuentasSinLimite() {
        assertThat(registrarDesde("203.0.113.10", uniqueEmail("r1")).status()).isEqualTo(200);
        assertThat(registrarDesde("203.0.113.10", uniqueEmail("r2")).status()).isEqualTo(200);

        Response tercera = registrarDesde("203.0.113.10", uniqueEmail("r3"));
        assertThat(tercera.status()).isEqualTo(429);
        assertThat(tercera.message()).contains("desde esta red");

        // Otra red sí puede
        assertThat(registrarDesde("203.0.113.11", uniqueEmail("r4")).status()).isEqualTo(200);
    }

    @Test
    void probarContrasenasEnVariasCuentasBloqueaLaRed() {
        String ip = "198.51.100.20";
        for (int i = 0; i < 3; i++) {
            Response r = callFrom(ip, "POST", "/auth/login", Map.of("email", uniqueEmail("nadie"), "password", "Medellin2026"), null);
            assertThat(r.status()).isEqualTo(401);
        }
        // Ya ni siquiera una contraseña correcta entra desde esa red
        Response bloqueada = callFrom(ip, "POST", "/auth/login", Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD), null);
        assertThat(bloqueada.status()).isEqualTo(429);

        // Desde otra red, la cuenta funciona normal
        assertThat(callFrom("198.51.100.21", "POST", "/auth/login", Map.of("email", ADMIN_EMAIL, "password", ADMIN_PASSWORD), null)
                .status()).isEqualTo(200);
    }

    @Test
    void losCorreosDeRecuperacionTienenTopePorRed() {
        String email = uniqueEmail("rec");
        registerVerified(email);

        assertThat(callFrom("192.0.2.30", "POST", "/auth/forgot-password", Map.of("email", email), null).status()).isEqualTo(204);
        verify(emailService).sendPasswordReset(anyString(), anyString(), anyString(), anyInt());

        // Segunda solicitud desde la misma red: responde igual pero no envía nada
        String otro = uniqueEmail("rec2");
        registerVerified(otro);
        assertThat(callFrom("192.0.2.30", "POST", "/auth/forgot-password", Map.of("email", otro), null).status()).isEqualTo(204);
        verify(emailService, never()).sendPasswordReset(org.mockito.ArgumentMatchers.eq(otro), anyString(), anyString(), anyInt());
    }
}
