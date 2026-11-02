package com.bibliospace.backend;

import com.bibliospace.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuditApiTests extends ApiTestBase {

    private List<String> detalles(Response r) {
        List<String> lista = new ArrayList<>();
        for (JsonNode item : r.body().get("items")) {
            lista.add(item.get("accion").asString() + " | " + item.get("usuarioEmail").asString() + " | " + item.get("detalle").asString());
        }
        return lista;
    }

    @Test
    void lasAccionesDelPanelQuedanRegistradasConQuienLasHizo() {
        String admin = adminToken();
        String email = uniqueEmail("auditada");
        long id = registerVerified(email).body().get("id").asLong();

        patch("/users/" + id, Map.of("activo", false), admin);
        long espacio = newSpace();

        Response r = get("/admin/audit?q=" + email, admin);
        assertThat(r.status()).isEqualTo(200);
        assertThat(detalles(r)).anySatisfy(d -> assertThat(d).startsWith("USUARIO_SUSPENDIDO | " + ADMIN_EMAIL).contains(email));

        Response todos = get("/admin/audit?tamano=100", admin);
        assertThat(detalles(todos)).anySatisfy(d -> assertThat(d).startsWith("ESPACIO_CREADO | " + ADMIN_EMAIL));
        assertThat(espacio).isPositive();
    }

    @Test
    void laBusquedaFiltraYLasPaginasIndicanSiHayMas() {
        String admin = adminToken();
        String unico = uniqueEmail("pagina");
        long id = registerVerified(unico).body().get("id").asLong();
        patch("/users/" + id, Map.of("activo", false), admin);
        patch("/users/" + id, Map.of("activo", true), admin);

        Response primera = get("/admin/audit?q=" + unico + "&tamano=1", admin);

        assertThat(primera.body().get("total").asLong()).isEqualTo(2);
        assertThat(primera.body().get("items")).hasSize(1);
        assertThat(primera.body().get("hayMas").asBoolean()).isTrue();
        // Lo más reciente primero
        assertThat(primera.body().get("items").get(0).get("accion").asString()).isEqualTo("USUARIO_REACTIVADO");
    }

    @Test
    void soloElAdministradorVeElRegistro() {
        assertThat(get("/admin/audit", newStudent()).status()).isEqualTo(403);
        assertThat(get("/admin/audit", newUserWithRole("ROLE_FUNCIONARIO")).status()).isEqualTo(403);
    }
}
