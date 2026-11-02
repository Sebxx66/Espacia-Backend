package com.bibliospace.backend.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

// Decide a qué dirección del frontend apuntan los enlaces de los correos.
// El frontend envía la dirección desde la que se está usando (por ejemplo, la IP del PC en la red
// cuando se abre desde el celular). Solo se acepta si es una dirección conocida o de red local;
// cualquier otra se reemplaza por app.frontend-url, para que nadie pueda hacer que el correo
// apunte a un sitio externo.
@Component
public class AppUrlResolver {

    private final String frontendUrl;
    private final Set<String> origenesPermitidos;

    public AppUrlResolver(@Value("${app.frontend-url:http://localhost:8443}") String frontendUrl,
                          @Value("${app.cors.allowed-origins:}") String[] allowedOrigins) {
        this.frontendUrl = sinBarraFinal(frontendUrl);
        this.origenesPermitidos = Arrays.stream(allowedOrigins)
                .map(String::trim).filter(o -> !o.isEmpty())
                .map(AppUrlResolver::sinBarraFinal)
                .collect(Collectors.toSet());
    }

    public String resolver(String solicitada) {
        if (solicitada == null || solicitada.isBlank()) {
            return frontendUrl;
        }
        try {
            URI uri = URI.create(solicitada.trim());
            String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost();
            if (!(esquema.equals("http") || esquema.equals("https")) || host == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty() && !uri.getPath().equals("/"))) {
                return frontendUrl;
            }
            String origen = esquema + "://" + host.toLowerCase(Locale.ROOT) + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            if (origen.equals(frontendUrl) || origenesPermitidos.contains(origen) || esRedLocal(host)) {
                return origen;
            }
        } catch (IllegalArgumentException e) {
            // dirección mal formada: se usa la configurada
        }
        return frontendUrl;
    }

    // localhost o una IPv4 privada (10.x, 172.16-31.x, 192.168.x)
    static boolean esRedLocal(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.equals("127.0.0.1")) {
            return true;
        }
        String[] p = h.split("\\.");
        if (p.length != 4) {
            return false;
        }
        int[] n = new int[4];
        for (int i = 0; i < 4; i++) {
            if (!p[i].matches("\\d{1,3}")) {
                return false;
            }
            n[i] = Integer.parseInt(p[i]);
            if (n[i] > 255) {
                return false;
            }
        }
        return n[0] == 10 || (n[0] == 172 && n[1] >= 16 && n[1] <= 31) || (n[0] == 192 && n[1] == 168);
    }

    private static String sinBarraFinal(String url) {
        return url == null ? "" : url.trim().replaceAll("/+$", "");
    }
}
