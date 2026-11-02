package com.bibliospace.backend;

import com.bibliospace.backend.services.EmailService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

// El envío por API (Brevo y Resend) contra un servidor HTTP local que hace de proveedor
class EmailApiProviderTests {

    private HttpServer server;
    private final AtomicReference<String> cuerpo = new AtomicReference<>();
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private final AtomicReference<String> autorizacion = new AtomicReference<>();

    @BeforeEach
    void iniciar() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/enviar", ex -> {
            cuerpo.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            apiKey.set(ex.getRequestHeaders().getFirst("api-key"));
            autorizacion.set(ex.getRequestHeaders().getFirst("Authorization"));
            byte[] ok = "{\"id\":\"1\"}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(201, ok.length);
            ex.getResponseBody().write(ok);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void detener() {
        server.stop(0);
    }

    @SuppressWarnings("unchecked")
    private EmailService servicio(String proveedor, String clave, String remitente) {
        ObjectProvider<JavaMailSender> sinSmtp = Mockito.mock(ObjectProvider.class);
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/enviar";
        return new EmailService(sinSmtp, "", "Espacia", "https://espacia.example", proveedor, clave, remitente, url);
    }

    @Test
    void brevoRecibeElRemitenteElDestinoYLaClave() throws Exception {
        EmailService email = servicio("brevo", "clave-brevo", "avisos@espacia.example");
        assertThat(email.enabled()).isTrue();

        email.sendWelcome("ana@test.local", "Ana");

        JsonNode json = JsonMapper.builder().build().readTree(cuerpo.get());
        assertThat(apiKey.get()).isEqualTo("clave-brevo");
        assertThat(json.get("sender").get("email").asString()).isEqualTo("avisos@espacia.example");
        assertThat(json.get("sender").get("name").asString()).isEqualTo("Espacia");
        assertThat(json.get("to").get(0).get("email").asString()).isEqualTo("ana@test.local");
        assertThat(json.get("htmlContent").asString()).contains("Ana");
    }

    @Test
    void resendUsaElEncabezadoAuthorization() throws Exception {
        EmailService email = servicio("resend", "re_123", "avisos@espacia.example");

        email.sendReservationNotice("ana@test.local", "Ana", "CONFIRMACION", "Tu reserva quedó confirmada.");

        JsonNode json = JsonMapper.builder().build().readTree(cuerpo.get());
        assertThat(autorizacion.get()).isEqualTo("Bearer re_123");
        assertThat(json.get("from").asString()).isEqualTo("Espacia <avisos@espacia.example>");
        assertThat(json.get("to").get(0).asString()).isEqualTo("ana@test.local");
        assertThat(json.get("subject").asString()).contains("Reserva confirmada");
    }

    @Test
    void sinClaveOSinRemitenteElEnvioQuedaDesactivado() {
        assertThat(servicio("brevo", "", "avisos@espacia.example").enabled()).isFalse();
        assertThat(servicio("resend", "re_123", "").enabled()).isFalse();
        assertThat(servicio("otro", "x", "a@b.co").enabled()).isFalse();
    }
}
