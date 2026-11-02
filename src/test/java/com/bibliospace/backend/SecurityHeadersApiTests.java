package com.bibliospace.backend;

import com.bibliospace.backend.support.ApiTestBase;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityHeadersApiTests extends ApiTestBase {

    @Test
    void laApiEnviaCabecerasDeSeguridad() {
        HttpResponse<String> r = rawResponse("/api/v1/rules");

        assertThat(r.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(r.headers().firstValue("X-Frame-Options")).hasValue("DENY");
        assertThat(r.headers().firstValue("Content-Security-Policy")).hasValue("default-src 'none'; frame-ancestors 'none'");
        assertThat(r.headers().firstValue("Referrer-Policy")).hasValue("strict-origin-when-cross-origin");
        assertThat(r.headers().firstValue("Permissions-Policy")).isPresent();
    }

    @Test
    void laDocumentacionNoLlevaLaPoliticaEstrictaDeLaApi() {
        HttpResponse<String> r = rawResponse("/v3/api-docs");

        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.headers().firstValue("Content-Security-Policy")).isEmpty();
    }
}
