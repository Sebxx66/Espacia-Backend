package com.bibliospace.backend.services;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

// IP de quien hace la petición, para los límites por red.
// Detrás de Render la conexión llega desde su proxy (Cloudflare), que pone la IP real en "CF-Connecting-IP";
// en un PC o un servidor propio, sin esa cabecera, se usa la IP de la conexión.
@Component
public class ClientIpResolver {

    private final String cabecera;

    public ClientIpResolver(@Value("${app.security.client-ip-header:CF-Connecting-IP}") String cabecera) {
        this.cabecera = cabecera == null ? "" : cabecera.trim();
    }

    public String actual() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return "desconocida";
        }
        return de(attrs.getRequest());
    }

    String de(HttpServletRequest request) {
        if (!cabecera.isEmpty()) {
            String valor = request.getHeader(cabecera);
            if (valor != null) {
                String ip = valor.split(",")[0].trim();
                if (esIp(ip)) {
                    return ip;
                }
            }
        }
        String remota = request.getRemoteAddr();
        return remota == null ? "desconocida" : remota;
    }

    private static boolean esIp(String texto) {
        return !texto.isEmpty() && texto.length() <= 45 && texto.matches("[0-9A-Fa-f.:]+");
    }
}
