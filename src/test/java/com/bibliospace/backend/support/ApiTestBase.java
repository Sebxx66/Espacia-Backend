package com.bibliospace.backend.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Base de las pruebas de la API: levanta el backend completo en un puerto aleatorio
// (con H2 en memoria) y lo llama por HTTP, igual que lo hace el frontend.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ApiTestBase {

    protected static final String ADMIN_EMAIL = "admin@test.local";
    protected static final String ADMIN_PASSWORD = "admin-pruebas-123";
    protected static final String PASSWORD = "clave-segura-1";

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    protected static final ObjectMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    private int port;

    public record Response(int status, JsonNode body) {
        public String message() {
            return body.path("message").asString();
        }
    }

    protected Response call(String method, String path, Object body, String token) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                    .method(method, body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            if (body != null) req.header("Content-Type", "application/json");
            if (token != null) req.header("Authorization", "Bearer " + token);
            HttpResponse<String> res = HTTP.send(req.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode json = res.body() == null || res.body().isBlank() ? JSON.createObjectNode() : JSON.readTree(res.body());
            return new Response(res.statusCode(), json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected Response get(String path, String token) {
        return call("GET", path, null, token);
    }

    protected Response post(String path, Object body, String token) {
        return call("POST", path, body, token);
    }

    protected Response patch(String path, Object body, String token) {
        return call("PATCH", path, body, token);
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
    }

    protected String login(String email, String password) {
        Response r = post("/auth/login", Map.of("email", email, "password", password), null);
        assertThat(r.status()).as("login de %s: %s", email, r.body()).isEqualTo(200);
        return r.body().get("token").asString();
    }

    protected String adminToken() {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    // Registra un estudiante nuevo y devuelve su token
    protected String newStudent() {
        Response r = post("/auth/register", Map.of("nombre", "Estudiante", "email", uniqueEmail("est"), "password", PASSWORD), null);
        assertThat(r.status()).as("registro: %s", r.body()).isEqualTo(200);
        return r.body().get("token").asString();
    }

    // El administrador crea una cuenta con el rol indicado; devuelve el token de esa cuenta
    protected String newUserWithRole(String rol) {
        String email = uniqueEmail(rol.toLowerCase());
        Response r = post("/users", Map.of("nombre", "Usuario " + rol, "email", email, "password", PASSWORD, "rol", rol), adminToken());
        assertThat(r.status()).as("crear usuario: %s", r.body()).isEqualTo(200);
        return login(email, PASSWORD);
    }

    // Crea un espacio activo nuevo para que cada prueba tenga horarios libres
    protected long newSpace() {
        return newSpace("SALA_ESTUDIO");
    }

    protected long newSpace(String tipo) {
        Response r = post("/spaces", Map.of(
                "nombre", "Espacio " + UUID.randomUUID().toString().substring(0, 6),
                "tipo", tipo,
                "capacidad", 4,
                "estado", "ACTIVO"), adminToken());
        assertThat(r.status()).as("crear espacio: %s", r.body()).isEqualTo(200);
        return r.body().get("id").asLong();
    }

    protected Response reserve(String token, long spaceId, LocalDate fecha, String inicio, String fin) {
        return post("/reservations", Map.of(
                "espacioId", spaceId,
                "fecha", fecha.toString(),
                "horaInicio", inicio,
                "horaFin", fin), token);
    }

    protected static LocalDate tomorrow() {
        return LocalDate.now().plusDays(1);
    }
}
