package com.bibliospace.backend.services;

import com.bibliospace.backend.exceptions.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

// Límites sencillos en memoria (se reinician al reiniciar el backend):
//  - inicio de sesión: tras 5 contraseñas incorrectas en 15 minutos, la cuenta se bloquea 15 minutos
//  - recuperar contraseña: máximo 3 solicitudes por correo cada hora
//  - por red (IP): tope de cuentas nuevas por hora, de correos de verificación o recuperación por hora
//    y de contraseñas incorrectas en 15 minutos aunque sean de cuentas distintas.
//    Los topes por IP son amplios porque en una universidad muchos estudiantes comparten la misma red.
//  - tope general de cuentas nuevas por hora, para no agotar el cupo de correos si alguien usa muchas redes
@Service
public class RateLimitService {

    private static final Duration HORA = Duration.ofHours(1);

    private final int registrosPorIp;
    private final int registrosTotales;
    private final int correosPorIp;
    private final int fallosPorIp;

    public RateLimitService(
            @Value("${app.limits.registros-por-ip-hora:20}") int registrosPorIp,
            @Value("${app.limits.registros-por-hora:100}") int registrosTotales,
            @Value("${app.limits.correos-por-ip-hora:20}") int correosPorIp,
            @Value("${app.limits.fallos-login-por-ip:30}") int fallosPorIp) {
        this.registrosPorIp = registrosPorIp;
        this.registrosTotales = registrosTotales;
        this.correosPorIp = correosPorIp;
        this.fallosPorIp = fallosPorIp;
    }

    private static final int MAX_FALLOS = 5;
    private static final Duration VENTANA_LOGIN = Duration.ofMinutes(15);
    private static final Duration BLOQUEO = Duration.ofMinutes(15);
    private static final int MAX_RECUPERACIONES = 3;
    private static final int MAX_ENTRADAS = 5000;

    private static final class Intentos {
        int fallos;
        Instant desde = Instant.now();
        Instant bloqueadoHasta;
    }

    private final Map<String, Intentos> login = new HashMap<>();
    private final Map<String, Deque<Instant>> recuperaciones = new HashMap<>();

    // Lanza 429 si la cuenta está bloqueada
    public synchronized void checkLogin(String clave) {
        Intentos i = login.get(clave);
        if (i == null || i.bloqueadoHasta == null) {
            return;
        }
        Instant ahora = Instant.now();
        if (ahora.isBefore(i.bloqueadoHasta)) {
            long minutos = Math.max(1, Duration.between(ahora, i.bloqueadoHasta).toMinutes() + 1);
            throw ApiException.tooManyRequests("Demasiados intentos fallidos. Intenta de nuevo en "
                    + minutos + (minutos == 1 ? " minuto." : " minutos."));
        }
        login.remove(clave);
    }

    public synchronized void loginFailed(String clave) {
        loginFailed(clave, MAX_FALLOS);
    }

    private void loginFailed(String clave, int maximo) {
        Instant ahora = Instant.now();
        Intentos i = login.computeIfAbsent(clave, k -> new Intentos());
        if (Duration.between(i.desde, ahora).compareTo(VENTANA_LOGIN) > 0) {
            i.fallos = 0;
            i.desde = ahora;
        }
        i.fallos++;
        if (i.fallos >= maximo) {
            i.bloqueadoHasta = ahora.plus(BLOQUEO);
        }
        limpiar(ahora);
    }

    public synchronized void loginOk(String clave) {
        login.remove(clave);
    }

    // true si se permite la solicitud; false si ya superó el límite
    public synchronized boolean allowPasswordReset(String clave) {
        return permitir(clave, MAX_RECUPERACIONES);
    }

    // --- Límites por red (IP) ---

    // Lanza 429 si desde esa red ya se crearon muchas cuentas en la última hora, o si hay demasiados registros en general
    public synchronized void checkRegistro(String ip) {
        if (!disponible("registro-total", registrosTotales)) {
            throw ApiException.tooManyRequests("Hay muchos registros en este momento. Intenta de nuevo en unos minutos.");
        }
        if (!disponible("registro-ip:" + ip, registrosPorIp)) {
            throw ApiException.tooManyRequests("Se crearon demasiadas cuentas desde esta red en la última hora. Intenta más tarde.");
        }
    }

    // Cuenta un registro aceptado
    public synchronized void registroHecho(String ip) {
        permitir("registro-total", registrosTotales);
        permitir("registro-ip:" + ip, registrosPorIp);
    }

    // true si se puede enviar otro correo de verificación o recuperación desde esa red
    public synchronized boolean allowEmailFromIp(String ip) {
        return permitir("correo-ip:" + ip, correosPorIp);
    }

    // Lanza 429 si esa red acumuló demasiadas contraseñas incorrectas (aunque sean de cuentas distintas)
    public synchronized void checkLoginIp(String ip) {
        checkLogin("ip:" + ip);
    }

    // Los aciertos no reinician el conteo por red: así no se puede alternar con una cuenta propia
    public synchronized void loginFailedIp(String ip) {
        loginFailed("ip:" + ip, fallosPorIp);
    }

    // Ventana de una hora: true y anota la solicitud si no se ha llegado al máximo
    private boolean permitir(String clave, int maximo) {
        Deque<Instant> cola = vigentes(clave);
        if (cola.size() >= maximo) {
            return false;
        }
        cola.addLast(Instant.now());
        return true;
    }

    private boolean disponible(String clave, int maximo) {
        return vigentes(clave).size() < maximo;
    }

    private Deque<Instant> vigentes(String clave) {
        Instant ahora = Instant.now();
        limpiar(ahora);
        Deque<Instant> cola = recuperaciones.computeIfAbsent(clave, k -> new ArrayDeque<>());
        while (!cola.isEmpty() && Duration.between(cola.peekFirst(), ahora).compareTo(HORA) > 0) {
            cola.pollFirst();
        }
        return cola;
    }

    // Evita que los mapas crezcan sin límite
    private void limpiar(Instant ahora) {
        if (login.size() > MAX_ENTRADAS) {
            login.values().removeIf(i -> i.bloqueadoHasta == null
                    ? Duration.between(i.desde, ahora).compareTo(VENTANA_LOGIN) > 0
                    : ahora.isAfter(i.bloqueadoHasta));
        }
        if (recuperaciones.size() > MAX_ENTRADAS) {
            recuperaciones.values().removeIf(c -> c.isEmpty()
                    || Duration.between(c.peekLast(), ahora).compareTo(HORA) > 0);
        }
    }
}
