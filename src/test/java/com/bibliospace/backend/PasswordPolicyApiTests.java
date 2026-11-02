package com.bibliospace.backend;

import com.bibliospace.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyApiTests extends ApiTestBase {

    private Response registrar(String email, String nombre, String password) {
        return post("/auth/register", Map.of("nombre", nombre, "email", email, "password", password, "aceptaPrivacidad", true), null);
    }

    @Test
    void elRegistroRechazaContrasenasComunesOPredecibles() {
        for (String debil : new String[]{"12345678", "password2026!", "Contraseña123", "Espacia2026!", "qwertyuiop", "aaaaaaaa", "87654321"}) {
            Response r = registrar(uniqueEmail("debil"), "Ana", debil);
            assertThat(r.status()).as("debía rechazar %s", debil).isEqualTo(400);
            assertThat(r.message()).contains("muy común");
        }
        assertThat(registrar(uniqueEmail("fuerte"), "Ana", "mi perro come arepas").status()).isEqualTo(200);
    }

    @Test
    void laContrasenaNoPuedeContenerElCorreoNiElNombre() {
        String email = "marcela.gomez-" + System.nanoTime() + "@test.local";

        Response conCorreo = registrar(email, "Ana", "marcela-segura-9");
        Response conNombre = registrar(uniqueEmail("nombre"), "Marcela Gómez", "gomez2026xyz!");

        assertThat(conCorreo.status()).isEqualTo(400);
        assertThat(conCorreo.message()).contains("correo ni tu nombre");
        assertThat(conNombre.status()).isEqualTo(400);
        assertThat(conNombre.message()).contains("correo ni tu nombre");
    }

    @Test
    void laReglaAplicaAlCambiarYAlAsignarContrasenas() {
        String email = uniqueEmail("cambio");
        Response cuenta = registerVerified(email);
        String token = cuenta.body().get("token").asString();
        long id = cuenta.body().get("id").asLong();

        assertThat(patch("/auth/password", Map.of("actual", PASSWORD, "nueva", "password123"), token).status()).isEqualTo(400);
        assertThat(patch("/users/" + id + "/password", Map.of("password", "abcdefgh"), adminToken()).status()).isEqualTo(400);
        assertThat(post("/users", Map.of("nombre", "Func", "email", uniqueEmail("func"), "password", "qwerty123", "rol", "ROLE_FUNCIONARIO"),
                adminToken()).status()).isEqualTo(400);

        // La contraseña de antes sigue funcionando: solo se revisan las nuevas
        assertThat(post("/auth/login", Map.of("email", email, "password", PASSWORD), null).status()).isEqualTo(200);
    }
}
